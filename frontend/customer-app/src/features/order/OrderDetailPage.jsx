import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { ordersApi } from '../../services/apiOrders.js';
import { reviewsApi as myReviewsApi } from '../../services/apiMyReviews.js';
import { reviewsApi } from '../../services/apiReviews.js';
import { useAuth } from '../../contexts/AuthContext.jsx';
import { useRealtimeEvent } from '../../hooks/useRealtime.js';
import { Button, useConfirmDialog } from '../../components/index.js';
import CancelOrderModal from '../../components/CancelOrderModal.jsx';
import {
    ORDER_STATUS,
    PAYMENT_STATUS,
    isCancellable,
    isReviewable,
    statusColor,
    statusLabel,
} from '../../utils/orderMeta.js';
import { formatCurrency, formatDate } from '../../utils/format.js';
import usePageTitle from '../../hooks/usePageTitle.js';
import './OrderDetailPage.css';

/**
 * OrderDetailPage — GET /orders/{id}
 *
 * Sections:
 *   1. Header (order number, status, payment status)
 *   2. Status timeline (from order.statusHistory)
 *   3. Items + shipping address + summary
 *   4. Actions:
 *      - Cancel (if PENDING or CONFIRMED) → CancelOrderModal + POST /orders/{id}/cancel
 *      - Review (if DELIVERED) for each item not yet reviewed
 *   5. Realtime: subscribes to order.status.updated for this orderId; on
 *      match, re-fetches the order and toasts.
 */
export default function OrderDetailPage() {
    const { id } = useParams();
    const { isAuthenticated } = useAuth();
    const { confirm, dialog } = useConfirmDialog();

    const [order, setOrder] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionBusy, setActionBusy] = useState(null);
    const [reviewedProducts, setReviewedProducts] = useState(() => new Set());
    const [cancelModalOpen, setCancelModalOpen] = useState(false);

    usePageTitle(order?.orderNumber || 'Đơn hàng');

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
            .getOrderById(id)
            .then((data) => {
                if (cancelled) return;
                setOrder(data);
                setLoading(false);
                // If delivered, fetch the reviews to know which items were already reviewed
                if (data.status === 'DELIVERED') {
                    loadReviewed(data);
                }
            })
            .catch((err) => {
                if (cancelled) return;
                setError(err);
                setLoading(false);
            });

        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [id, isAuthenticated]);

    // Find which items the user has already reviewed (so we know whether
    // to show the review form).
    async function loadReviewed(orderData) {
        const reviewed = new Set();
        for (const item of orderData.items || []) {
            try {
                const resp = await reviewsApi.listForProduct(item.productId, { page: 0, size: 50 });
                const mine = (resp.reviews || []).filter(
                    (r) => r.orderId === orderData.id && r.userId === orderData.customerId
                );
                if (mine.length > 0) reviewed.add(item.productId);
            } catch {
                // best-effort; if it fails, just show the review form
            }
        }
        setReviewedProducts(reviewed);
    }

    // -------- realtime: refresh order + status badge on event --------
    useRealtimeEvent('order.status.updated', (payload) => {
        if (payload?.orderId !== id) return;
        toast.success(`Đơn ${order?.orderNumber ?? ''}: ${statusLabel(payload.status)}`);
        // Re-fetch full order to update timeline + status
        ordersApi
            .getOrderById(id)
            .then(setOrder)
            .catch(() => { /* ignore — toast still shown */ });
    }, isAuthenticated);

    // -------- handlers --------
    const openCancelModal = () => {
        if (actionBusy) return;
        setCancelModalOpen(true);
    };

    const closeCancelModal = () => {
        if (actionBusy === 'cancel') return;
        setCancelModalOpen(false);
    };

    const handleCancelConfirm = async (reason) => {
        setActionBusy('cancel');
        try {
            const updated = await ordersApi.cancelOrder(order.id, reason);
            setOrder(updated);
            toast.success('Đã huỷ đơn hàng');
            setCancelModalOpen(false);
        } catch {
            // toast already shown by axios interceptor
        } finally {
            setActionBusy(null);
        }
    };

    const handleReviewSubmit = async (productId, reviewPayload) => {
        setActionBusy(`review:${productId}`);
        try {
            await myReviewsApi.create(productId, {
                orderId: order.id,
                ...reviewPayload,
            });
            toast.success('Cảm ơn bạn đã đánh giá sản phẩm!');
            setReviewedProducts((prev) => new Set(prev).add(productId));
        } catch {
            // toast already shown
        } finally {
            setActionBusy(null);
        }
    };

    // -------- render --------
    if (loading) {
        return (
            <div className="container order-detail-page">
                <div className="order-detail-page__loading">Đang tải đơn hàng...</div>
            </div>
        );
    }

    if (error || !order) {
        return (
            <div className="container order-detail-page">
                <div className="order-detail-page__error">
                    <h2>Không thể tải đơn hàng</h2>
                    <p>{error?.message || 'Đơn hàng không tồn tại hoặc bạn không có quyền truy cập.'}</p>
                    <Link to="/orders" className="order-detail-page__back">← Quay lại danh sách</Link>
                </div>
            </div>
        );
    }

    return (
        <div className="container order-detail-page">
            <nav className="order-detail-page__breadcrumb">
                <Link to="/orders">Đơn hàng của tôi</Link> / <span>{order.orderNumber}</span>
            </nav>

            {/* ===== Header ===== */}
            <header className="order-detail-page__header">
                <div>
                    <h1>Đơn hàng {order.orderNumber}</h1>
                    <p className="order-detail-page__date">Đặt ngày {formatDate(order.createdAt)}</p>
                </div>
                <div className="order-detail-page__badges">
                    <span className={`order-status order-status--${statusColor(order.status)}`}>
                        {statusLabel(order.status)}
                    </span>
                    {order.paymentStatus && (
                        <span className={`order-status order-status--${PAYMENT_STATUS[order.paymentStatus]?.color || ''}`}>
                            {PAYMENT_STATUS[order.paymentStatus]?.label || order.paymentStatus}
                        </span>
                    )}
                </div>
            </header>

            {/* ===== Timeline ===== */}
            <section className="order-detail-page__section">
                <h2>Trạng thái đơn hàng</h2>
                <OrderTimeline
                    currentStatus={order.status}
                    history={order.statusHistory || []}
                />
            </section>

            {/* ===== Items ===== */}
            <section className="order-detail-page__section">
                <h2>Sản phẩm ({order.items?.length || 0})</h2>
                <ul className="order-detail-page__items">
                    {order.items?.map((item) => (
                        <li key={item.productId} className="order-detail-page__item">
                            <Link to={`/products/${item.productId}`} className="order-detail-page__item-link">
                                <span className="order-detail-page__item-name">{item.productName}</span>
                                <span className="order-detail-page__item-meta">
                                    {formatCurrency(item.unitPrice)} × {item.quantity}
                                </span>
                                <span className="order-detail-page__item-subtotal">
                                    {formatCurrency(item.subtotal)}
                                </span>
                            </Link>

                            {isReviewable(order.status) && !reviewedProducts.has(item.productId) && (
                                <ReviewForm
                                    productId={item.productId}
                                    productName={item.productName}
                                    onSubmit={(p) => handleReviewSubmit(item.productId, p)}
                                    busy={actionBusy === `review:${item.productId}`}
                                />
                            )}
                            {isReviewable(order.status) && reviewedProducts.has(item.productId) && (
                                <p className="order-detail-page__item-reviewed">✓ Bạn đã đánh giá sản phẩm này</p>
                            )}
                        </li>
                    ))}
                </ul>
            </section>

            {/* ===== Address + Summary ===== */}
            <section className="order-detail-page__section order-detail-page__layout-2">
                <div className="order-detail-page__address">
                    <h3>Địa chỉ giao hàng</h3>
                    {order.shippingAddress ? (
                        <address>
                            <strong>{order.shippingAddress.label}</strong>
                            <p>{order.shippingAddress.line1}</p>
                            <p>
                                {order.shippingAddress.ward}, {order.shippingAddress.district}, {order.shippingAddress.city}
                            </p>
                            {order.shippingAddress.phone && <p>📞 {order.shippingAddress.phone}</p>}
                        </address>
                    ) : (
                        <p>Không có thông tin</p>
                    )}
                </div>

                <div className="order-detail-page__totals">
                    <h3>Thanh toán</h3>
                    <dl>
                        <div><dt>Tạm tính</dt><dd>{formatCurrency(order.subtotalAmount)}</dd></div>
                        {order.discountAmount != null && Number(order.discountAmount) > 0 && (
                            <div className="order-detail-page__total-row--discount">
                                <dt>
                                    Giảm giá
                                    {order.promotionCode && ` (${order.promotionCode})`}
                                </dt>
                                <dd>− {formatCurrency(order.discountAmount)}</dd>
                            </div>
                        )}
                        <div className="order-detail-page__total-row--final">
                            <dt>Tổng</dt>
                            <dd>{formatCurrency(order.totalAmount)}</dd>
                        </div>
                    </dl>
                </div>
            </section>

            {/* ===== Actions ===== */}
            <section className="order-detail-page__actions">
                {isCancellable(order.status) ? (
                    <Button
                        variant="outline"
                        size="md"
                        onClick={openCancelModal}
                        loading={actionBusy === 'cancel'}
                    >
                        Huỷ đơn hàng
                    </Button>
                ) : (
                    <p className="order-detail-page__action-hint">
                        {order.status === 'CANCELLED'
                            ? order.cancelReason
                                ? `Đơn hàng đã được huỷ. Lý do: ${order.cancelReason}`
                                : 'Đơn hàng đã được huỷ.'
                            : order.status === 'DELIVERED'
                            ? 'Đơn hàng đã giao thành công.'
                            : 'Đơn hàng đang được xử lý, không thể huỷ.'}
                    </p>
                )}
                <Link to="/products" className="order-detail-page__continue">← Tiếp tục mua sắm</Link>
            </section>

            {dialog}

            <CancelOrderModal
                open={cancelModalOpen}
                orderNumber={order.orderNumber}
                loading={actionBusy === 'cancel'}
                onConfirm={handleCancelConfirm}
                onCancel={closeCancelModal}
            />
        </div>
    );
}

// =============================================================
// Sub-components
// =============================================================

function OrderTimeline({ currentStatus, history }) {
    // Build a canonical list of expected states for the visual timeline
    const canonical = ['PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPING', 'DELIVERED'];
    const isCancelled = currentStatus === 'CANCELLED';
    const isReturned = currentStatus === 'RETURNED';

    // Map history into a quick lookup by status
    const historyByStatus = (history || []).reduce((acc, h) => {
        if (h.status) acc[h.status] = h;
        return acc;
    }, {});

    let activeIndex = canonical.indexOf(currentStatus);
    if (isCancelled || isReturned) activeIndex = -2;

    return (
        <ol className="timeline">
            {canonical.map((s, i) => (
                <li
                    key={s}
                    className={`timeline__step ${
                        isCancelled ? 'is-cancelled' :
                        i <= activeIndex ? 'is-done' : ''
                    } ${i === activeIndex ? 'is-active' : ''}`}
                >
                    <span className="timeline__dot">
                        {i < activeIndex || (i === activeIndex) ? '✓' : i + 1}
                    </span>
                    <span className="timeline__label">{statusLabel(s)}</span>
                    {historyByStatus[s]?.changedAt && (
                        <span className="timeline__date">{formatDate(historyByStatus[s].changedAt)}</span>
                    )}
                </li>
            ))}
            {isCancelled && (
                <li className="timeline__step timeline__step--final is-cancelled">
                    <span className="timeline__dot">✕</span>
                    <span className="timeline__label">Đã huỷ</span>
                </li>
            )}
            {isReturned && (
                <li className="timeline__step timeline__step--final is-returned">
                    <span className="timeline__dot">↩</span>
                    <span className="timeline__label">Đã trả hàng</span>
                </li>
            )}
        </ol>
    );
}

function ReviewForm({ productId, productName, onSubmit, busy }) {
    const [rating, setRating] = useState(5);
    const [comment, setComment] = useState('');
    const [showForm, setShowForm] = useState(false);

    if (!showForm) {
        return (
            <button
                type="button"
                className="review-form__trigger"
                onClick={() => setShowForm(true)}
            >
                ✎ Đánh giá sản phẩm
            </button>
        );
    }

    const handleSubmit = (e) => {
        e.preventDefault();
        onSubmit({ rating, comment: comment.trim() || undefined });
    };

    return (
        <form className="review-form" onSubmit={handleSubmit}>
            <p className="review-form__title">Đánh giá: <strong>{productName}</strong></p>
            <div className="review-form__rating">
                {[1, 2, 3, 4, 5].map((n) => (
                    <button
                        key={n}
                        type="button"
                        className={`review-form__star ${n <= rating ? 'is-filled' : ''}`}
                        onClick={() => setRating(n)}
                        aria-label={`${n} sao`}
                    >
                        ★
                    </button>
                ))}
            </div>
            <textarea
                className="review-form__textarea"
                placeholder="Chia sẻ cảm nhận của bạn về sản phẩm…"
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                rows={3}
                maxLength={2000}
            />
            <div className="review-form__actions">
                <button
                    type="button"
                    className="review-form__cancel"
                    onClick={() => setShowForm(false)}
                    disabled={busy}
                >
                    Huỷ
                </button>
                <Button type="submit" size="sm" loading={busy} disabled={busy}>
                    Gửi đánh giá
                </Button>
            </div>
        </form>
    );
}