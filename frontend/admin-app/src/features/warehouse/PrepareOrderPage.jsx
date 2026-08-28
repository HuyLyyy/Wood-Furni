import { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import usePageTitle from '../../hooks/usePageTitle.js';
import { useRealtime } from '../../hooks/useRealtime.js';
import { adminOrdersApi } from '../../services/apiAdminOrders.js';
import { Button } from '../../components/index.js';
import { statusLabel } from '../../utils/orderMeta.js';
import { formatCurrency, formatDateTime } from '../../utils/format.js';
import './PrepareOrderPage.css';

const PAGE_SIZE = 20;

/**
 * PrepareOrderPage — WAREHOUSE / ADMIN only.
 *
 * Lists all orders in PROCESSING status (waiting for warehouse to pack & ship).
 * WebSocket connection subscribes to the `order.ready_to_prepare` event so new
 * orders pushed by SALES flow in without a page refresh.
 *
 * Layout:
 *   [header: title + live-indicator]
 *   [stats bar: N orders waiting]
 *   [order cards: one card per order]
 *       - orderNumber + customer address
 *       - items table (product name, qty)
 *       - [Đã chuẩn bị xong] button
 */
export default function PrepareOrderPage() {
    usePageTitle('Chuẩn bị đơn hàng');
    const [orders, setOrders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [markingId, setMarkingId] = useState(null);

    // ── WebSocket ───────────────────────────────────────────────────────────
    const { socket, connected } = useRealtime(true);

    useEffect(() => {
        if (!socket || !connected) return;

        const handler = (payload) => {
            const { orderId, orderNumber } = payload || {};
            if (!orderId) return;

            // Prepend to list (dedup by orderId)
            setOrders((prev) => {
                if (prev.some((o) => o.id === orderId)) return prev;
                return [{ id: orderId, orderNumber: orderNumber || '', _isNew: true }, ...prev];
            });

            toast.success(
                <span>
                    Có đơn mới cần chuẩn bị{' '}
                    <strong>{orderNumber || orderId}</strong>
                </span>,
                { duration: 5000 }
            );
        };

        socket.on('order.ready_to_prepare', handler);
        return () => socket.off('order.ready_to_prepare', handler);
    }, [socket, connected]);

    // ── Load PROCESSING orders ──────────────────────────────────────────────
    const load = useCallback(async (pageNum = 0) => {
        setLoading(true);
        setError(null);
        try {
            const data = await adminOrdersApi.list({ status: 'PROCESSING', page: pageNum, size: PAGE_SIZE });
            setOrders(data.items || []);
            setPage(data.page || 0);
            setTotalPages(data.totalPages || 0);
        } catch (err) {
            setError(err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { load(); }, [load]);

    // ── Mark prepared ────────────────────────────────────────────────────────
    const handleMarkPrepared = async (orderId) => {
        setMarkingId(orderId);
        try {
            await adminOrdersApi.markPrepared(orderId);
            // Remove from list (no longer PROCESSING)
            setOrders((prev) => prev.filter((o) => o.id !== orderId));
            toast.success('Đơn đã được đánh dấu chuẩn bị xong.');
        } catch {
            void 0; // interceptor shows error toast
        } finally {
            setMarkingId(null);
        }
    };

    return (
        <div className="admin-page prepare-order-page">
            <div className="prepare-order-page__head">
                <div>
                    <h1>Chuẩn bị đơn hàng</h1>
                    <p className="prepare-order-page__sub">
                        Danh sách đơn hàng đang chờ đóng gói và giao cho đơn vị vận chuyển.
                    </p>
                </div>
                <div className="prepare-order-page__live-badge">
                    <span className={`live-dot ${connected ? 'live-dot--on' : 'live-dot--off'}`} />
                    {connected ? 'Live' : 'Offline'}
                </div>
            </div>

            {orders.length === 0 && !loading && (
                <div className="prepare-order-page__empty">
                    <span className="prepare-order-page__empty-icon">📦</span>
                    <p>Không có đơn hàng nào đang chờ chuẩn bị.</p>
                    <p className="muted">Đơn mới sẽ tự xuất hiện khi SALES gửi qua Warehouse.</p>
                </div>
            )}

            {error && (
                <div className="prepare-order-page__error">
                    <strong>Không thể tải đơn hàng.</strong> {error?.message}
                    <div style={{ marginTop: 8 }}>
                        <Button variant="primary" onClick={() => load()}>Thử lại</Button>
                    </div>
                </div>
            )}

            <div className="prepare-order-page__stats">
                <span>{orders.length} đơn đang chờ chuẩn bị</span>
            </div>

            {loading ? (
                <div className="prepare-order-page__loading">Đang tải...</div>
            ) : (
                <div className="prepare-order-page__list">
                    {orders.map((order) => (
                        <OrderCard
                            key={order.id}
                            order={order}
                            markingId={markingId}
                            onMarkPrepared={handleMarkPrepared}
                        />
                    ))}
                </div>
            )}

            {totalPages > 1 && (
                <div className="prepare-order-page__pagination">
                    <Button
                        variant="ghost"
                        disabled={page === 0}
                        onClick={() => load(page - 1)}
                    >
                        ← Trang trước
                    </Button>
                    <span>Trang {page + 1} / {totalPages}</span>
                    <Button
                        variant="ghost"
                        disabled={page >= totalPages - 1}
                        onClick={() => load(page + 1)}
                    >
                        Trang sau →
                    </Button>
                </div>
            )}
        </div>
    );
}

function OrderCard({ order, markingId, onMarkPrepared }) {
    const isMarking = markingId === order.id;
    const isNew = order._isNew;

    return (
        <div className={`order-card ${isNew ? 'order-card--new' : ''}`}>
            <div className="order-card__header">
                <div className="order-card__id">
                    <strong>{order.orderNumber || order.id}</strong>
                    {isNew && <span className="order-card__new-badge">Mới!</span>}
                </div>
                <div className="order-card__meta">
                    <span className="order-card__status">{statusLabel(order.status)}</span>
                    <span className="muted">·</span>
                    <span className="muted">{formatDateTime(order.createdAt)}</span>
                </div>
            </div>

            <div className="order-card__body">
                <section className="order-card__section">
                    <h4>Địa chỉ giao hàng</h4>
                    {order.shippingAddress ? (
                        <div className="address-card">
                            <div className="address-card__name">{order.shippingAddress.label}</div>
                            <div>{order.shippingAddress.line1}</div>
                            <div>
                                {[order.shippingAddress.ward, order.shippingAddress.district, order.shippingAddress.city]
                                    .filter(Boolean)
                                    .join(', ')}
                            </div>
                            <div>ĐT: {order.shippingAddress.phone}</div>
                        </div>
                    ) : (
                        <p className="muted">Không có</p>
                    )}
                </section>

                <section className="order-card__section">
                    <h4>Sản phẩm cần đóng gói ({order.items?.length || 0})</h4>
                    <table className="order-card__items">
                        <thead>
                            <tr>
                                <th>Sản phẩm</th>
                                <th className="numeric">SL</th>
                            </tr>
                        </thead>
                        <tbody>
                            {(order.items || []).map((it, i) => (
                                <tr key={`${it.productId}-${i}`}>
                                    <td>{it.productName || '—'}</td>
                                    <td className="numeric"><strong>{it.quantity}</strong></td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </section>
            </div>

            <div className="order-card__footer">
                <div className="order-card__total">
                    Tổng: <strong>{formatCurrency(order.totalAmount)}</strong>
                </div>
                <Button
                    variant="primary"
                    disabled={isMarking}
                    onClick={() => onMarkPrepared(order.id)}
                >
                    {isMarking ? 'Đang xử lý...' : '✓ Đã chuẩn bị xong'}
                </Button>
            </div>
        </div>
    );
}
