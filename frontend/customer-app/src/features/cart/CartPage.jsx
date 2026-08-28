import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useCart } from '../../contexts/CartContext.jsx';
import { useAuth } from '../../contexts/AuthContext.jsx';
import { promotionsApi } from '../../services/apiPromotions.js';
import { Button, Input, useConfirmDialog } from '../../components/index.js';
import { formatCurrency } from '../../utils/format.js';
import './CartPage.css';

/**
 * CartPage — GET /cart (auto on mount) + actions here.
 *
 * UX:
 *   - Each item has a checkbox so the customer can pick which items to
 *     checkout right now. Items left unchecked stay in the cart for a
 *     later checkout.
 *   - "Chọn tất cả / Bỏ chọn tất cả" toggles all checkboxes at once.
 *   - Quantity +/- debounced via direct call (no submit button)
 *   - Delete → confirm dialog
 *   - Voucher input → POST /promotions/validate (preview discount only,
 *     the actual /orders/checkout will re-validate on the server)
 *   - "Tiến hành Checkout" → navigate /checkout (passes code + selected
 *     productIds via router state so CheckoutPage can echo them back)
 *
 * Note: totals always come from the server response (in CartContext state)
 * so we display `cart.totalAmount` and `cart.itemCount`.
 */
export default function CartPage() {
    const { cart, loading, actionBusy, updateItem, removeItem, clear } = useCart();
    const { isAuthenticated } = useAuth();
    const navigate = useNavigate();
    const { confirm, dialog } = useConfirmDialog();

    const [voucherCode, setVoucherCode] = useState('');
    const [voucherPreview, setVoucherPreview] = useState(null);
    const [voucherLoading, setVoucherLoading] = useState(false);
    const [voucherError, setVoucherError] = useState(null);

    // Selected product IDs — checked items are the ones that will be
    // included in the next checkout. Default to "all selected" so the
    // existing single-checkout flow keeps working out of the box.
    const [selected, setSelected] = useState(() => new Set());

    const items = cart?.items || [];
    const subtotal = cart?.totalAmount ?? 0;

    // Whenever the cart contents change (initial fetch, add, remove,
    // quantity change, refresh after order), reset the selection to
    // "all checked". Customers who uncheck things stay unchecked between
    // item updates, but a brand-new item shows up checked by default.
    useEffect(() => {
        setSelected(new Set(items.map((it) => it.productId)));
    }, [items.length]);

    // ── selection math ──────────────────────────────────────────────────
    const allSelected = items.length > 0 && items.every((it) => selected.has(it.productId));
    const someSelected = items.some((it) => selected.has(it.productId));

    const selectedItems = useMemo(
        () => items.filter((it) => selected.has(it.productId)),
        [items, selected]
    );
    const selectedSubtotal = useMemo(
        () => selectedItems.reduce((sum, it) => sum + Number(it.subtotal || 0), 0),
        [selectedItems]
    );
    const selectedCount = selectedItems.length;

    const discount = useMemo(() => {
        if (!voucherPreview?.valid) return 0;
        // The promo discount is applied to the order subtotal, which
        // equals our selectedSubtotal at checkout time. We use the
        // server-returned discountAmount as-is — the backend re-validates
        // the amount at /orders/checkout so the customer can't game this.
        return Number(voucherPreview.discountAmount) || 0;
    }, [voucherPreview]);

    const finalTotal = Math.max(0, selectedSubtotal - discount);

    // ── selection handlers ──────────────────────────────────────────────
    const handleToggleOne = (productId) => {
        setSelected((prev) => {
            const next = new Set(prev);
            if (next.has(productId)) {
                next.delete(productId);
            } else {
                next.add(productId);
            }
            return next;
        });
    };

    const handleToggleAll = () => {
        if (allSelected) {
            setSelected(new Set());
        } else {
            setSelected(new Set(items.map((it) => it.productId)));
        }
    };

    // ── quantity handlers ───────────────────────────────────────────────
    const handleQtyChange = async (productId, newQty) => {
        const n = parseInt(newQty, 10);
        if (!Number.isFinite(n) || n < 0) return;
        try {
            if (n === 0) {
                await removeItem(productId);
            } else {
                await updateItem(productId, n);
            }
        } catch {
            // toast already shown
        }
    };

    // ── delete with confirm ─────────────────────────────────────────────
    const handleRemove = async (product) => {
        const ok = await confirm({
            title: 'Xoá sản phẩm?',
            message: `Bạn có chắc muốn xoá "${product.productName}" khỏi giỏ hàng?`,
            confirmLabel: 'Xoá',
            danger: true,
        });
        if (ok) {
            try {
                await removeItem(product.productId);
                // Also drop it from the selection so the badge stays accurate.
                setSelected((prev) => {
                    if (!prev.has(product.productId)) return prev;
                    const next = new Set(prev);
                    next.delete(product.productId);
                    return next;
                });
                toast.success('Đã xoá sản phẩm');
            } catch {
                // toast already shown
            }
        }
    };

    const handleClear = async () => {
        const ok = await confirm({
            title: 'Xoá toàn bộ giỏ hàng?',
            message: 'Hành động này không thể hoàn tác.',
            confirmLabel: 'Xoá tất cả',
            danger: true,
        });
        if (ok) {
            try {
                await clear();
                toast.success('Đã xoá toàn bộ giỏ hàng');
                setVoucherPreview(null);
                setVoucherCode('');
                setSelected(new Set());
            } catch {
                // toast already shown
            }
        }
    };

    // ── voucher preview ─────────────────────────────────────────────────
    const handleApplyVoucher = async () => {
        const code = voucherCode.trim().toUpperCase();
        if (!code) {
            setVoucherError('Vui lòng nhập mã voucher');
            return;
        }
        if (selectedSubtotal <= 0) {
            setVoucherError('Vui lòng chọn ít nhất một sản phẩm');
            return;
        }
        setVoucherLoading(true);
        setVoucherError(null);
        try {
            const result = await promotionsApi.validate(code, selectedSubtotal);
            setVoucherPreview(result);
            if (!result.valid) {
                setVoucherError(result.message || 'Mã voucher không hợp lệ');
            } else {
                toast.success(`Áp dụng mã ${code} — giảm ${formatCurrency(result.discountAmount)}`);
            }
        } catch (err) {
            setVoucherPreview(null);
            setVoucherError(err?.message || 'Mã voucher không hợp lệ');
        } finally {
            setVoucherLoading(false);
        }
    };

    const handleClearVoucher = () => {
        setVoucherCode('');
        setVoucherPreview(null);
        setVoucherError(null);
    };

    // ── checkout ────────────────────────────────────────────────────────
    const handleCheckout = () => {
        if (!isAuthenticated) {
            navigate('/login', { state: { from: '/cart' } });
            return;
        }
        if (selectedCount === 0) {
            toast.error('Vui lòng chọn ít nhất một sản phẩm để thanh toán');
            return;
        }
        // Always send a list. Backend treats it as "all" when empty/null,
        // but we always have a non-empty list here so the backend only
        // checks out the selected items.
        const productIds = items
            .filter((it) => selected.has(it.productId))
            .map((it) => it.productId);
        navigate('/checkout', {
            state: {
                voucherCode: voucherPreview?.valid ? voucherCode.trim().toUpperCase() : null,
                productIds,
            },
        });
    };

    // ── render ──────────────────────────────────────────────────────────

    if (!isAuthenticated) {
        return (
            <div className="container cart-page">
                <div className="cart-page__empty">
                    <h2>Vui lòng đăng nhập</h2>
                    <p>Đăng nhập để xem giỏ hàng và tiến hành thanh toán.</p>
                    <Link to="/login" className="cart-page__cta">Đăng nhập</Link>
                </div>
            </div>
        );
    }

    if (loading && !cart) {
        return (
            <div className="container cart-page">
                <div className="cart-page__loading">Đang tải giỏ hàng...</div>
            </div>
        );
    }

    return (
        <div className="container cart-page">
            <header className="cart-page__header">
                <h1>Giỏ hàng</h1>
                <span className="cart-page__count">
                    {cart?.itemCount ?? 0} sản phẩm
                    {someSelected && (
                        <>
                            {' '}· <strong>{selectedCount}</strong> đã chọn
                        </>
                    )}
                </span>
            </header>

            {items.length === 0 ? (
                <div className="cart-page__empty">
                    <p>Giỏ hàng của bạn đang trống.</p>
                    <Link to="/products" className="cart-page__cta">Khám phá sản phẩm</Link>
                </div>
            ) : (
                <div className="cart-page__layout">
                    {/* ===== Items list ===== */}
                    <section className="cart-page__items">
                        {/* "Select all" toolbar */}
                        <div className="cart-page__select-all">
                            <label>
                                <input
                                    type="checkbox"
                                    checked={allSelected}
                                    ref={(el) => {
                                        if (el) el.indeterminate = !allSelected && someSelected;
                                    }}
                                    onChange={handleToggleAll}
                                />
                                <span>
                                    {allSelected
                                        ? 'Bỏ chọn tất cả'
                                        : someSelected
                                            ? `Chọn tất cả (${items.length})`
                                            : `Chọn tất cả (${items.length})`}
                                </span>
                            </label>
                        </div>

                        {items.map((item) => {
                            const isChecked = selected.has(item.productId);
                            return (
                                <article
                                    key={item.productId}
                                    className={`cart-item ${actionBusy === `upd:${item.productId}` || actionBusy === `del:${item.productId}` ? 'is-busy' : ''} ${!isChecked ? 'is-unselected' : ''}`}
                                >
                                    <label className="cart-item__checkbox">
                                        <input
                                            type="checkbox"
                                            checked={isChecked}
                                            onChange={() => handleToggleOne(item.productId)}
                                            aria-label={`Chọn ${item.productName} để thanh toán`}
                                        />
                                    </label>

                                    <Link to={`/products/${item.productId}`} className="cart-item__image">
                                        <img
                                            src={item.productImage || '/placeholder-product.svg'}
                                            alt={item.productName}
                                            onError={(e) => {
                                                e.currentTarget.src = '/placeholder-product.svg';
                                            }}
                                        />
                                    </Link>

                                    <div className="cart-item__info">
                                        <Link to={`/products/${item.productId}`} className="cart-item__name">
                                            {item.productName}
                                        </Link>
                                        <span className="cart-item__unit-price">
                                            {formatCurrency(item.unitPrice)} / sản phẩm
                                        </span>
                                        <button
                                            type="button"
                                            className="cart-item__remove"
                                            onClick={() => handleRemove(item)}
                                            disabled={actionBusy === `del:${item.productId}`}
                                        >
                                            🗑 Xoá
                                        </button>
                                    </div>

                                    <div className="cart-item__qty">
                                        <button
                                            type="button"
                                            onClick={() => handleQtyChange(item.productId, item.quantity - 1)}
                                            disabled={item.quantity <= 1 || actionBusy === `upd:${item.productId}`}
                                            aria-label="Giảm"
                                        >
                                            −
                                        </button>
                                        <input
                                            type="number"
                                            min="1"
                                            value={item.quantity}
                                            onChange={(e) => handleQtyChange(item.productId, e.target.value)}
                                            disabled={actionBusy === `upd:${item.productId}`}
                                        />
                                        <button
                                            type="button"
                                            onClick={() => handleQtyChange(item.productId, item.quantity + 1)}
                                            disabled={actionBusy === `upd:${item.productId}`}
                                            aria-label="Tăng"
                                        >
                                            +
                                        </button>
                                    </div>

                                    <div className="cart-item__subtotal">
                                        {formatCurrency(item.subtotal)}
                                    </div>
                                </article>
                            );
                        })}

                        <div className="cart-page__items-foot">
                            <button
                                type="button"
                                className="cart-page__clear-btn"
                                onClick={handleClear}
                                disabled={actionBusy === 'clear'}
                            >
                                {actionBusy === 'clear' ? 'Đang xoá...' : 'Xoá toàn bộ giỏ hàng'}
                            </button>
                            <Link to="/products" className="cart-page__continue">← Tiếp tục mua sắm</Link>
                        </div>
                    </section>

                    {/* ===== Summary ===== */}
                    <aside className="cart-page__summary">
                        <h2 className="cart-page__summary-title">Tóm tắt đơn hàng</h2>

                        <div className="cart-page__voucher">
                            <label className="cart-page__voucher-label">Mã khuyến mãi</label>
                            <div className="cart-page__voucher-input">
                                <Input
                                    id="voucher"
                                    placeholder="Nhập mã (vd: WELCOME10)"
                                    value={voucherCode}
                                    onChange={(e) => {
                                        setVoucherCode(e.target.value.toUpperCase());
                                        if (voucherError) setVoucherError(null);
                                    }}
                                    error={voucherError}
                                    disabled={voucherLoading}
                                />
                                <Button
                                    variant="outline"
                                    size="md"
                                    onClick={handleApplyVoucher}
                                    loading={voucherLoading}
                                    disabled={!voucherCode.trim() || selectedCount === 0}
                                >
                                    Áp dụng
                                </Button>
                            </div>
                            {voucherPreview?.valid && (
                                <div className="cart-page__voucher-success">
                                    ✓ Đã áp dụng <strong>{voucherPreview.code}</strong> — giảm {formatCurrency(voucherPreview.discountAmount)}
                                    <button type="button" onClick={handleClearVoucher}>Bỏ</button>
                                </div>
                            )}
                        </div>

                        <dl className="cart-page__totals">
                            {selectedCount < items.length && (
                                <div className="cart-page__total-row" style={{ color: '#94a3b8', fontSize: 13 }}>
                                    <dt>Tổng giỏ hàng ({items.length} sp)</dt>
                                    <dd style={{ textDecoration: 'line-through' }}>
                                        {formatCurrency(subtotal)}
                                    </dd>
                                </div>
                            )}
                            <div className="cart-page__total-row">
                                <dt>
                                    Tạm tính ({selectedCount} sp
                                    {selectedCount < items.length ? ' đã chọn' : ''})
                                </dt>
                                <dd>{formatCurrency(selectedSubtotal)}</dd>
                            </div>
                            {voucherPreview?.valid && (
                                <div className="cart-page__total-row cart-page__total-row--discount">
                                    <dt>Giảm giá ({voucherPreview.code})</dt>
                                    <dd>− {formatCurrency(discount)}</dd>
                                </div>
                            )}
                            <div className="cart-page__total-row cart-page__total-row--final">
                                <dt>Thành tiền</dt>
                                <dd>{formatCurrency(finalTotal)}</dd>
                            </div>
                        </dl>

                        <Button
                            variant="primary"
                            size="lg"
                            fullWidth
                            onClick={handleCheckout}
                            disabled={selectedCount === 0}
                        >
                            {selectedCount === 0
                                ? 'Chọn sản phẩm để thanh toán'
                                : selectedCount < items.length
                                    ? `Tiến hành Checkout ${selectedCount}/${items.length} sp →`
                                    : 'Tiến hành Checkout →'}
                        </Button>

                        <p className="cart-page__hint">
                            {selectedCount < items.length
                                ? `Sản phẩm chưa chọn sẽ được giữ lại trong giỏ cho lần thanh toán sau.`
                                : 'Voucher chỉ là preview. Mã sẽ được xác nhận lại khi bạn đặt hàng.'}
                        </p>
                    </aside>
                </div>
            )}
            {dialog}
        </div>
    );
}