import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import usePageTitle from '../../hooks/usePageTitle.js';
import { useAuth } from '../../contexts/AuthContext.jsx';
import { adminOrdersApi } from '../../services/apiAdminOrders.js';
import {
    Button, FormField, useConfirmDialog,
} from '../../components/index.js';
import {
    ORDER_STATUS, statusLabel, statusTone,
    paymentStatusLabel, paymentMethodLabel,
    nextStatuses,
} from '../../utils/orderMeta.js';
import { formatCurrency, formatDateTime } from '../../utils/format.js';
import { can } from '../../utils/permissions.js';
import ReceiveReturnModal from './ReceiveReturnModal.jsx';
import './OrderDetailPage.css';

/**
 * OrderDetailPage
 *
 *   - Header: orderNumber, status badge, createdAt
 *   - State machine action bar (only valid next statuses from current)
 *   - Customer info card + shipping address
 *   - Items table (snapshot!)
 *   - Totals (subtotal / discount / total)
 *   - Timeline (statusHistory)
 *
 * State machine actions only POST PATCH /orders/{id}/status. Cancel is
 * also routed through the same endpoint (status=CANCELLED is part of
 * nextStatuses).
 *
 * Role-based filtering:
 *   - SALES can drive PENDING → CONFIRMED, CONFIRMED → PROCESSING (via the
 *     dedicated "Gửi qua Warehouse" button), and SHIPPING → DELIVERED
 *     (via tracking updates / PATCH). They CANNOT flip PROCESSING → SHIPPING
 *     themselves — that is exclusively Warehouse Staff's "đã chuẩn bị xong"
 *     step.
 *   - WAREHOUSE can drive PROCESSING → SHIPPING (via /mark-prepared) and
 *     SHIPPING → DELIVERED (via tracking updates).
 *   - ADMIN can drive any transition.
 *
 * Both the dedicated buttons (Gửi qua Warehouse) and the generic state-
 * machine buttons respect this rule, so a SALES user looking at a
 * PROCESSING order will not see a "→ Đang giao" button — they have to wait
 * for Warehouse to confirm preparation before the next step becomes
 * available to them.
 */
export default function OrderDetailPage() {
    usePageTitle('Chi tiết đơn hàng');
    const { id } = useParams();
    const navigate = useNavigate();
    const { user } = useAuth();
    const role = user?.role;
    const { confirm, dialog } = useConfirmDialog();

    const canUpdateStatus = can(role, 'orders:updateStatus');
    const canSendToWarehouse = can(role, 'orders:sendToWarehouse');
    const canMarkPrepared = can(role, 'orders:markPrepared');
    const canCancel = can(role, 'orders:cancel');
    const canReceiveReturn = can(role, 'orders:receiveReturn');
    const canForceCancelPromo = can(role, 'orders:forceCancelPromo');

    const [order, setOrder] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [acting, setActing] = useState(false);
    const [showReceiveReturn, setShowReceiveReturn] = useState(false);

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await adminOrdersApi.getById(id);
            setOrder(data);
        } catch (err) {
            setError(err);
        } finally {
            setLoading(false);
        }
    }, [id]);

    useEffect(() => { load(); }, [load]);

    const apply = useCallback(async (action, run) => {
        const ok = await confirm({
            title: action.title,
            message: action.message,
            confirmLabel: action.confirmLabel || 'Xác nhận',
            danger: action.danger,
        });
        if (!ok) return;
        setActing(true);
        try {
            const next = await run();
            setOrder(next);
            toast.success(action.successMessage || 'Đã cập nhật');
        } catch (err) {
            // toast handled by interceptor
            void err;
        } finally {
            setActing(false);
        }
    }, [confirm]);

    const handleTransition = (nextStatus) => {
        const status = ORDER_STATUS.find((s) => s.value === nextStatus);
        const action = {
            title: `Chuyển trạng thái → ${status?.label || nextStatus}?`,
            message: `Đơn ${order.orderNumber} sẽ chuyển sang "${status?.label || nextStatus}". Hành động này sẽ được ghi vào lịch sử đơn.`,
            confirmLabel: 'Chuyển trạng thái',
            danger: nextStatus === 'CANCELLED',
            successMessage: `Đã chuyển sang ${status?.label || nextStatus}`,
        };
        apply(action, () => adminOrdersApi.updateStatus(order.id, nextStatus));
    };

    const handleSendToWarehouse = () => {
        apply(
            {
                title: 'Gửi đơn qua Warehouse?',
                message: `Đơn ${order.orderNumber} sẽ được chuyển sang trạng thái "Đang xử lý" và thông báo tới nhân viên kho. Sau bước này, Sales phải đợi nhân viên Warehouse xác nhận "đã chuẩn bị xong" trước khi có thể thao tác tiếp các chức năng "đang giao".`,
                confirmLabel: 'Gửi qua Warehouse',
                successMessage: 'Đơn đã được gửi qua Warehouse.',
            },
            () => adminOrdersApi.sendToWarehouse(order.id)
        );
    };

    const handleMarkPrepared = () => {
        apply(
            {
                title: 'Xác nhận đã chuẩn bị xong?',
                message: `Đơn ${order.number || order.orderNumber} đã được chuẩn bị xong và sẵn sàng giao. Trạng thái sẽ chuyển sang "Đang giao" và Sales có thể tiếp tục cập nhật tracking / xác nhận giao hàng.`,
                confirmLabel: 'Đã chuẩn bị xong',
                successMessage: 'Đơn đã sẵn sàng giao.',
            },
            () => adminOrdersApi.markPrepared(order.id)
        );
    };

    const handleReceiveReturn = () => {
        setShowReceiveReturn(true);
    };

    const handleReceiveReturnConfirm = async ({ items, note }) => {
        setActing(true);
        setShowReceiveReturn(false);
        try {
            const next = await adminOrdersApi.receiveReturn(order.id, items, note);
            setOrder(next);
            toast.success('Đã xác nhận nhận lại hàng.');
        } catch (err) {
            void err;
        } finally {
            setActing(false);
        }
    };

    const handleForceCancelPromo = async () => {
        const ok = await confirm({
            title: 'Hủy đơn hàng có khuyến mãi?',
            message: 'Đơn hàng có mã khuyến mãi sẽ được hủy và hoàn tiền. Hành động này không thể hoàn tác.',
            confirmText: 'Hủy đơn & hoàn tiền',
            variant: 'danger',
        });
        if (!ok) return;
        const reason = window.prompt('Lý do hủy (tùy chọn):', '') || null;
        setActing(true);
        try {
            const next = await adminOrdersApi.forceCancelPromo(order.id, reason);
            setOrder(next);
            toast.success('Đã hủy đơn hàng có khuyến mãi và hoàn tiền.');
        } catch (err) {
            void err;
        } finally {
            setActing(false);
        }
    };

    /**
     * Role-aware filter on the next-status list.
     *
     * Each transition is restricted to the role that owns it. The generic
     * PATCH /orders/{id}/status endpoint enforces the same matrix on the
     * server, but we hide the button here so SALES never sees a button
     * they cannot press anyway.
     */
    const allowedTransitionsForRole = (statusList) => {
        if (role === 'ADMIN') return statusList; // ADMIN catch-all
        return statusList.filter((s) => {
            // SALES owns PENDING → CONFIRMED and SHIPPING → DELIVERED.
            if (role === 'SALES') {
                return s === 'CONFIRMED' || s === 'DELIVERED';
            }
            // WAREHOUSE owns PROCESSING → SHIPPING (renders via markPrepared
            // dedicated button below, not via this generic list) and
            // SHIPPING → DELIVERED.
            if (role === 'WAREHOUSE') {
                return s === 'DELIVERED';
            }
            return false;
        });
    };

    if (loading) {
        return <div className="admin-page"><p>Đang tải...</p></div>;
    }
    if (error) {
        return (
            <div className="admin-page">
                <div className="detail-error">
                    <strong>Không thể tải đơn hàng.</strong> {error?.message}
                    <div style={{ marginTop: 8 }}>
                        <Button variant="primary" onClick={load}>Thử lại</Button>{' '}
                        <Button variant="ghost" onClick={() => navigate('/orders')}>← Quay lại</Button>
                    </div>
                </div>
            </div>
        );
    }
    if (!order) return null;

    const validNext = nextStatuses(order.status);
    const tone = statusTone(order.status);
    const subtotal = order.subtotalAmount
        ?? (order.items || []).reduce((s, it) => s + Number(it.subtotal || 0), 0);
    const discount = Number(order.discountAmount || 0);
    const total = Number(order.totalAmount || 0);

    return (
        <div className="admin-page order-detail-page">
            <div className="order-detail-page__main">
                <div className="order-detail-page__crumbs">
                    <Link to="/orders">Đơn hàng</Link> <span>›</span> <span>{order.orderNumber}</span>
                </div>

                <header className="order-detail-page__head">
                    <h1>{order.orderNumber}</h1>
                    <span className={`status-badge status-badge--${tone}`}>{statusLabel(order.status)}</span>
                </header>

                <div className="order-detail-page__meta">
                    <span>Khách: <strong>#{order.customerId?.slice(-6) || '—'}</strong></span>
                    <span>Ngày tạo: {formatDateTime(order.createdAt)}</span>
                    <span>Thanh toán: {paymentStatusLabel(order.paymentStatus)}</span>
                    <span>Phương thức: {paymentMethodLabel(order.paymentMethod)}</span>
                </div>

                {/* SALES + CONFIRMED → show dedicated "Gửi qua Warehouse" button.
                    After this click the order is in PROCESSING and Sales hands
                    off to Warehouse. Sales must NOT see the "→ Đang giao"
                    button until Warehouse has confirmed preparation. */}
                {canSendToWarehouse && order.status === 'CONFIRMED' && (
                    <section className="order-detail-page__section">
                        <h3>Hành động</h3>
                        <div className="action-row">
                            <Button
                                variant="primary"
                                disabled={acting}
                                onClick={handleSendToWarehouse}
                            >
                                📦 Gửi qua Warehouse
                            </Button>
                        </div>
                    </section>
                )}

                {/* WAREHOUSE + PROCESSING → dedicated "Đã chuẩn bị xong" button.
                    This is the step Sales is waiting on — once pressed, the
                    order becomes SHIPPING and the generic state-machine
                    buttons below become available to SALES again. */}
                {canMarkPrepared && order.status === 'PROCESSING' && (
                    <section className="order-detail-page__section">
                        <h3>Hành động</h3>
                        <div className="action-row">
                            <Button
                                variant="primary"
                                disabled={acting}
                                onClick={handleMarkPrepared}
                            >
                                ✅ Đã chuẩn bị xong
                            </Button>
                        </div>
                    </section>
                )}

                {/* SALES/ADMIN + SHIPPING → "Nhận lại hàng" button.
                    When the delivery person returns with undelivered items,
                    Sales/Admin records what was actually received.
                    This opens the ReceiveReturnModal. */}
                {canReceiveReturn && order.status === 'SHIPPING' && (
                    <section className="order-detail-page__section">
                        <h3>Nhận hàng trả lại</h3>
                        <p style={{ color: '#64748b', fontSize: 14, margin: '0 0 12px' }}>
                            Khi nhân viên giao hàng mang hàng chưa giao được về, nhấn nút bên dưới để xác nhận
                            số lượng sản phẩm khách hàng thực sự nhận được.
                        </p>
                        <div className="action-row">
                            <Button
                                variant="warning"
                                disabled={acting}
                                onClick={handleReceiveReturn}
                            >
                                📥 Nhận lại hàng từ NVGH
                            </Button>
                            {canForceCancelPromo && order.promotionCode && (
                                <Button
                                    variant="danger"
                                    disabled={acting}
                                    onClick={handleForceCancelPromo}
                                >
                                    🛑 Hủy đơn & hoàn tiền (KM)
                                </Button>
                            )}
                        </div>
                    </section>
                )}

                {/* ADMIN + SHIPPING + has promotion → same force-cancel button,
                    but rendered outside the ReceiveReturnModal block so it
                    also shows when canReceiveReturn is false (e.g. WAREHOUSE-only
                    account, or when status is DELIVERED). */}
                {canForceCancelPromo && order.promotionCode
                    && (order.status === 'SHIPPING' || order.status === 'DELIVERED')
                    && !(canReceiveReturn && order.status === 'SHIPPING') && (
                    <section className="order-detail-page__section">
                        <h3>Hủy đơn có khuyến mãi</h3>
                        <p style={{ color: '#64748b', fontSize: 14, margin: '0 0 12px' }}>
                            Đơn hàng đang ở trạng thái <strong>{statusLabel(order.status)}</strong>{' '}
                            và đang áp dụng mã khuyến mãi <strong>{order.promotionCode}</strong>.
                            Vì chính sách khuyến mãi không cho phép nhận một phần, bạn có thể hủy đơn và hoàn tiền cho khách.
                        </p>
                        <div className="action-row">
                            <Button
                                variant="danger"
                                disabled={acting}
                                onClick={handleForceCancelPromo}
                            >
                                🛑 Hủy đơn & hoàn tiền (KM)
                            </Button>
                        </div>
                    </section>
                )}

                {/* Generic status machine buttons — visibility filtered by role
                    so SALES never sees "→ Đang giao" while the order is in
                    PROCESSING. They only see transitions they can actually
                    drive (PENDING → CONFIRMED, SHIPPING → DELIVERED). */}
                {canUpdateStatus && order.status !== 'CONFIRMED' && order.status !== 'PROCESSING' && (
                    <section className="order-detail-page__section">
                        <h3>Hành động (theo state machine)</h3>
                        <div className="action-row">
                            {allowedTransitionsForRole(validNext).map((s) => {
                                const meta = ORDER_STATUS.find((x) => x.value === s);
                                const isCancel = s === 'CANCELLED';
                                const allowed = isCancel ? (canCancel || canUpdateStatus) : canUpdateStatus;
                                if (!allowed) return null;
                                return (
                                    <Button
                                        key={s}
                                        variant={isCancel ? 'danger' : 'primary'}
                                        disabled={acting}
                                        onClick={() => handleTransition(s)}
                                    >
                                        → {meta?.label || s}
                                    </Button>
                                );
                            })}
                        </div>
                    </section>
                )}

                {/* Helper text for SALES users who are waiting on Warehouse. */}
                {role === 'SALES' && order.status === 'PROCESSING' && (
                    <section className="order-detail-page__section">
                        <p style={{ color: '#94a3b8', fontStyle: 'italic', margin: 0 }}>
                            ⏳ Đơn đang được Warehouse chuẩn bị. Bạn sẽ có thể thao tác
                            tiếp các chức năng "đang giao" sau khi nhân viên Warehouse
                            xác nhận "đã chuẩn bị xong".
                        </p>
                    </section>
                )}

                <section className="order-detail-page__section">
                    <h3>Sản phẩm ({order.items?.length || 0})</h3>
                    <table className="order-detail-page__items">
                        <thead>
                            <tr>
                                <th>Sản phẩm</th>
                                <th className="numeric">SL đặt</th>
                                {order.status === 'DELIVERED' && <th className="numeric">SL nhận</th>}
                                <th className="numeric">Đơn giá</th>
                                <th className="numeric">Thành tiền</th>
                            </tr>
                        </thead>
                        <tbody>
                            {(order.items || []).map((it, i) => (
                                <tr key={`${it.productId}-${i}`}>
                                    <td>
                                        <div>{it.productName || '—'}</div>
                                        <small style={{ color: '#94a3b8' }}>SKU: {it.sku || '—'}</small>
                                    </td>
                                    <td className="numeric">{it.quantity}</td>
                                    {order.status === 'DELIVERED' && (
                                        <td className="numeric">
                                            {it.receivedQuantity != null && it.receivedQuantity !== it.quantity ? (
                                                <span style={{ color: '#d97706', fontWeight: 600 }}>
                                                    {it.receivedQuantity}
                                                    <span style={{ color: '#94a3b8', fontWeight: 400 }}>
                                                        {' '}/ {it.quantity}
                                                    </span>
                                                </span>
                                            ) : (
                                                <span>{it.receivedQuantity ?? it.quantity}</span>
                                            )}
                                        </td>
                                    )}
                                    <td className="numeric">{formatCurrency(it.unitPrice)}</td>
                                    <td className="numeric">{formatCurrency(it.subtotal)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>

                    <table className="order-detail-page__totals" style={{ marginTop: 16 }}>
                        <tbody>
                            <tr>
                                <td>Tạm tính</td>
                                <td style={{ textAlign: 'right' }}>{formatCurrency(subtotal)}</td>
                            </tr>
                            {discount > 0 && (
                                <tr>
                                    <td>Giảm giá {order.promotionCode ? `(${order.promotionCode})` : ''}</td>
                                    <td style={{ textAlign: 'right', color: '#16a34a' }}>−{formatCurrency(discount)}</td>
                                </tr>
                            )}
                            <tr className="grand">
                                <td>Tổng cộng</td>
                                <td style={{ textAlign: 'right' }}>{formatCurrency(total)}</td>
                            </tr>
                        </tbody>
                    </table>
                </section>
            </div>

            <aside className="order-detail-page__side">
                <section className="order-detail-page__section">
                    <h3>Địa chỉ giao hàng</h3>
                    {order.shippingAddress ? (
                        <div className="address-card">
                            <div className="address-card__name">{order.shippingAddress.label || ''}</div>
                            <div>{order.shippingAddress.line1}</div>
                            <div>
                                {order.shippingAddress.ward}
                                {order.shippingAddress.ward ? ', ' : ''}{order.shippingAddress.district}
                            </div>
                            <div>{order.shippingAddress.city}</div>
                            <div style={{ marginTop: 6 }}>
                                <strong>SĐT:</strong> {order.shippingAddress.phone}
                            </div>
                        </div>
                    ) : (
                        <p style={{ color: '#94a3b8' }}>Không có</p>
                    )}
                </section>

                <section className="order-detail-page__section">
                    <h3>Lịch sử trạng thái</h3>
                    <div className="timeline">
                        {(order.statusHistory || []).slice().reverse().map((entry, i, arr) => {
                            const isCurrent = i === 0;
                            return (
                                <div
                                    key={`${entry.status}-${entry.changedAt}-${i}`}
                                    className={`timeline__entry ${isCurrent ? 'timeline__entry--current' : ''}`}
                                >
                                    <div className="timeline__status">{statusLabel(entry.status)}</div>
                                    <div className="timeline__time">{formatDateTime(entry.changedAt)}</div>
                                    {entry.changedBy && (
                                        <div className="timeline__time">bởi #{entry.changedBy.slice(-6)}</div>
                                    )}
                                </div>
                            );
                        })}
                        {(!order.statusHistory || order.statusHistory.length === 0) && (
                            <p style={{ color: '#94a3b8' }}>Chưa có lịch sử</p>
                        )}
                    </div>
                </section>

                <Button variant="ghost" fullWidth onClick={() => navigate('/orders')}>← Quay lại danh sách</Button>
            </aside>

            {dialog}

            {showReceiveReturn && order && (
                <ReceiveReturnModal
                    order={order}
                    onClose={() => setShowReceiveReturn(false)}
                    onConfirm={handleReceiveReturnConfirm}
                />
            )}
        </div>
    );
}
