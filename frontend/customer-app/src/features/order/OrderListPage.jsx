import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { ordersApi } from '../../services/apiOrders.js';
import { useAuth } from '../../contexts/AuthContext.jsx';
import { useRealtimeEvent } from '../../hooks/useRealtime.js';
import { STATUSES_FOR_FILTER, statusLabel, statusColor } from '../../utils/orderMeta.js';
import { formatCurrency, formatDate } from '../../utils/format.js';
import usePageTitle from '../../hooks/usePageTitle.js';
import './OrderListPage.css';

/**
 * OrderListPage — GET /orders?status=&page=&size=
 *
 * URL is the source of truth for the status filter (matches the catalog
 * filter pattern). Sub-filter auto-rerenders this page.
 *
 * Realtime: each row subscribes to `order.status.updated` events. When a
 * payload's orderId matches the row, the row updates its status badge
 * in-place and shows a toast — no reload needed.
 */
export default function OrderListPage() {
    usePageTitle('Đơn hàng của tôi');
    const { isAuthenticated } = useAuth();
    const [searchParams, setSearchParams] = useSearchParams();
    const statusFilter = searchParams.get('status') || '';

    const [orders, setOrders] = useState([]);
    const [pagination, setPagination] = useState({ page: 0, totalPages: 0, totalElements: 0 });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const page = parseInt(searchParams.get('page') || '0', 10);

    // -------- fetch --------
    useEffect(() => {
        if (!isAuthenticated) {
            setLoading(false);
            return;
        }
        let cancelled = false;
        setLoading(true);
        setError(null);

        ordersApi
            .getOrders({ status: statusFilter || undefined, page, size: 20 })
            .then((data) => {
                if (cancelled) return;
                setOrders(data.items || []);
                setPagination({
                    page: data.page ?? 0,
                    totalPages: data.totalPages ?? 0,
                    totalElements: data.totalElements ?? 0,
                });
                setLoading(false);
            })
            .catch((err) => {
                if (cancelled) return;
                setError(err);
                setLoading(false);
            });

        return () => { cancelled = true; };
    }, [statusFilter, page, isAuthenticated]);

    const setStatusFilter = (value) => {
        setSearchParams(
            (prev) => {
                const next = new URLSearchParams(prev);
                if (!value) next.delete('status');
                else next.set('status', value);
                next.delete('page');
                return next;
            },
            { replace: false }
        );
    };

    const setPage = (p) => {
        setSearchParams(
            (prev) => {
                const next = new URLSearchParams(prev);
                if (p <= 0) next.delete('page');
                else next.set('page', String(p));
                return next;
            },
            { replace: true }
        );
    };

    // Realtime: also patch the local list if its items list covers the changed order
    useRealtimeEvent('order.status.updated', (payload) => {
        if (!payload?.orderId) return;
        setOrders((prev) =>
            prev.map((o) =>
                o.id === payload.orderId ? { ...o, status: payload.status } : o
            )
        );
    }, isAuthenticated);

    return (
        <div className="container order-list-page">
            <header className="order-list-page__header">
                <h1>Đơn hàng của tôi</h1>
                <p className="order-list-page__count">
                    {pagination.totalElements} đơn hàng
                </p>
            </header>

            <div className="order-list-page__filters">
                {STATUSES_FOR_FILTER.map((s) => (
                    <button
                        key={s.value || 'all'}
                        type="button"
                        className={`order-list-page__filter ${statusFilter === s.value ? 'is-active' : ''}`}
                        onClick={() => setStatusFilter(s.value)}
                    >
                        {s.label}
                    </button>
                ))}
            </div>

            {loading ? (
                <div className="order-list-page__loading">Đang tải đơn hàng...</div>
            ) : error ? (
                <div className="order-list-page__error">
                    <p>Không thể tải đơn hàng. Vui lòng thử lại.</p>
                    <p className="order-list-page__error-hint">{error?.message}</p>
                </div>
            ) : orders.length === 0 ? (
                <div className="order-list-page__empty">
                    <p>Bạn chưa có đơn hàng nào{statusFilter ? ' với trạng thái này' : ''}.</p>
                    <Link to="/products" className="order-list-page__cta">Mua sắm ngay</Link>
                </div>
            ) : (
                <>
                    <ul className="order-list">
                        {orders.map((order) => (
                            <li key={order.id}>
                                <OrderRow order={order} />
                            </li>
                        ))}
                    </ul>

                    {pagination.totalPages > 1 && (
                        <div className="order-list-page__pagination">
                            <button
                                type="button"
                                disabled={page <= 0}
                                onClick={() => setPage(page - 1)}
                            >
                                ‹ Trước
                            </button>
                            <span>Trang {page + 1} / {pagination.totalPages}</span>
                            <button
                                type="button"
                                disabled={page >= pagination.totalPages - 1}
                                onClick={() => setPage(page + 1)}
                            >
                                Sau ›
                            </button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}

function OrderRow({ order }) {
    const [currentStatus, setCurrentStatus] = useState(order.status);

    // Listen for live updates that target THIS order
    useRealtimeEvent('order.status.updated', (payload) => {
        if (payload?.orderId === order.id) {
            setCurrentStatus(payload.status);
            toast.success(`Đơn ${order.orderNumber}: ${statusLabel(payload.status)}`);
        }
    });

    return (
        <Link to={`/orders/${order.id}`} className="order-row">
            <div className="order-row__head">
                <span className="order-row__number">{order.orderNumber}</span>
                <span className={`order-status order-status--${statusColor(currentStatus)}`}>
                    {statusLabel(currentStatus)}
                </span>
            </div>
            <div className="order-row__body">
                <p className="order-row__items">
                    {order.items && order.items.length > 0
                        ? order.items
                              .slice(0, 3)
                              .map((i) => `${i.productName} ×${i.quantity}`)
                              .join(', ') + (order.items.length > 3 ? `…` : '')
                        : 'Không có sản phẩm'}
                </p>
                <div className="order-row__meta">
                    <span>{formatDate(order.createdAt)}</span>
                    <span className="order-row__total">{formatCurrency(order.totalAmount)}</span>
                </div>
            </div>
            <div className="order-row__arrow" aria-hidden="true">→</div>
        </Link>
    );
}