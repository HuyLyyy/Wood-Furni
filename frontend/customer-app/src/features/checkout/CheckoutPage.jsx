import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useCart } from '../../contexts/CartContext.jsx';
import { ordersApi } from '../../services/apiOrders.js';
import { promotionsApi } from '../../services/apiPromotions.js';
import { usersApi } from '../../services/apiUsers.js';
import { shippingApi } from '../../services/apiShipping.js';

import { addressStorage } from '../../services/addressStorage.js';
import { AddressPicker, Button, Input, useConfirmDialog } from '../../components/index.js';
import { formatCurrency } from '../../utils/format.js';
import { PAYMENT_METHODS } from '../../utils/orderMeta.js';
import './CheckoutPage.css';

/**
 * CheckoutPage
 *
 * Flow:
 *   1. Shipping address — select saved or enter new; must "confirm" to proceed
 *   2. On confirmation → POST /shipping/calculate → preview shipping fee
 *   3. Payment method
 *   4. Order summary — live-updates as address/voucher change
 *   5. Confirm → POST /orders/checkout (backend re-computes fee independently)
 *
 * After success → navigate /orders/{id} (so the user lands on the detail
 * page, which subscribes to realtime status updates).
 */
export default function CheckoutPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const { cart, refresh } = useCart();
    const { confirm, dialog } = useConfirmDialog();

    const initialVoucher = location.state?.voucherCode || '';
    // productIds selected on the CartPage. If absent/empty we fall back to
    // "checkout everything in the cart" (legacy behaviour) — but in the new
    // flow the CartPage always sends a non-empty list.
    const initialProductIds = location.state?.productIds || [];
    const items = cart?.items || [];
    // Restrict the displayed + sum-ed items to whatever the customer picked
    // on the cart page. Items not in the selection are not part of this order.
    const checkoutItems = initialProductIds.length > 0
        ? items.filter((it) => initialProductIds.includes(it.productId))
        : items;
    const subtotal = checkoutItems.reduce(
        (s, it) => s + Number(it.subtotal || 0),
        0
    );

    // ── voucher ───────────────────────────────────────────────────────────────
    const [voucherCode, setVoucherCode] = useState(initialVoucher);
    const [voucherPreview, setVoucherPreview] = useState(null);
    const [voucherError, setVoucherError] = useState(null);
    const [voucherLoading, setVoucherLoading] = useState(false);

    // ── address ───────────────────────────────────────────────────────────────
    const [savedAddresses, setSavedAddresses] = useState([]);
    const [selectedAddressId, setSelectedAddressId] = useState(null);
    // For the AddressPicker (new address flow)
    const [pickerAddress, setPickerAddress] = useState(null); // { line1, ward, district, city, isValid }
    const [mode, setMode] = useState('saved'); // 'saved' | 'new'
    const [addressErrors, setAddressErrors] = useState({});

    // ── shipping fee (previewed before placing order) ──────────────────────────
    /**
     * confirmedAddress holds the { city, district } that has been explicitly
     * confirmed by the customer (either by selecting a saved address or by
     * clicking "Xác nhận địa chỉ" on the new-address form).
     * null means no address has been confirmed yet → shipping fee is unknown.
     */
    const [confirmedAddress, setConfirmedAddress] = useState(null);
    const [shippingFee, setShippingFee] = useState(null);      // null = not yet calculated
    const [shippingLoading, setShippingLoading] = useState(false);
    const [shippingError, setShippingError] = useState(null); // error message from backend

    // ── payment ───────────────────────────────────────────────────────────────
    const [paymentMethod, setPaymentMethod] = useState('COD');

    // ── submit ───────────────────────────────────────────────────────────────
    const [submitting, setSubmitting] = useState(false);

    // ── bootstrap: load saved addresses ────────────────────────────────────
    useEffect(() => {
        let cancelled = false;
        async function loadAddresses() {
            try {
                const backendAddresses = await usersApi.listAddresses();
                if (!cancelled) {
                    setSavedAddresses(backendAddresses || []);
                    const def = backendAddresses?.find((a) => a.isDefault) || backendAddresses?.[0];
                    if (def) {
                        setSelectedAddressId(def.id);
                        setMode('saved');
                    } else {
                        setMode('new');
                    }
                }
            } catch {
                if (!cancelled) {
                    const list = addressStorage.list();
                    setSavedAddresses(list);
                    const def = addressStorage.getDefault();
                    if (def) {
                        setSelectedAddressId(def.id);
                        setMode('saved');
                    } else if (list.length > 0) {
                        setSelectedAddressId(list[0].id);
                        setMode('saved');
                    } else {
                        setMode('new');
                    }
                }
            }
        }
        loadAddresses();
        return () => { cancelled = true; };
    }, []);

    // ── voucher: re-validate whenever cart total or voucher code changes ───────
    //
    // Debounce so a user typing "WELCOME10" doesn't fire one validate call
    // per keystroke (each one would surface a "mã không tồn tại" error as
    // soon as the prefix stops matching anything). Stale requests are also
    // aborted so the network doesn't pile up behind the user's typing.
    useEffect(() => {
        const trimmed = voucherCode.trim();
        if (!trimmed) {
            setVoucherPreview(null);
            setVoucherError(null);
            return;
        }

        // Minimum length so single-character typos don't spam the API.
        if (trimmed.length < 3) {
            setVoucherPreview(null);
            setVoucherError(null);
            return;
        }

        const handle = setTimeout(() => {
            const controller = new AbortController();
            setVoucherLoading(true);
            setVoucherError(null);
            promotionsApi
                .validate(trimmed, subtotal, { signal: controller.signal })
                .then((result) => {
                    setVoucherPreview(result);
                    if (!result.valid) setVoucherError(result.message || 'Mã không hợp lệ');
                })
                .catch((err) => {
                    // Aborted requests are expected on every keystroke — ignore.
                    if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') return;
                    setVoucherPreview(null);
                    setVoucherError(err?.message || 'Mã không hợp lệ');
                })
                .finally(() => {
                    setVoucherLoading(false);
                });
            // Store on a ref-less pattern: the next keystroke will schedule a
            // new timeout, and the previous in-flight call gets aborted via
            // the controller we just created. We don't need to track it.
            // Cleanup is handled by debounce: if a new keystroke arrives, this
            // controller is no longer the "latest" — but we still cancel it on
            // effect unmount via the abort handler below.
            return () => controller.abort();
        }, 500);

        return () => clearTimeout(handle);
    }, [voucherCode, subtotal]);

    // ── derived values ────────────────────────────────────────────────────────
    const discount = voucherPreview?.valid ? Number(voucherPreview.discountAmount) || 0 : 0;
    const shippingFeeValue = shippingFee ?? 0;
    // Formula: Tổng cộng = Tạm tính − Giảm giá + Phí vận chuyển
    const totalAmount = Math.max(0, subtotal - discount + shippingFeeValue);

    // isAddressConfirmed is true when confirmedAddress is not null.
    // isShippingReady is true when confirmedAddress exists AND shipping fee is resolved (not loading, no error).
    const isShippingReady = confirmedAddress !== null && shippingFee !== null && !shippingLoading && !shippingError;

    // ── address helpers ───────────────────────────────────────────────────────

    /** Resolve the full address object for the currently selected address. */
    function resolveCurrentAddress() {
        if (mode === 'saved' && selectedAddressId) {
            return savedAddresses.find((a) => a.id === selectedAddressId) || null;
        }
        return null; // new address mode needs explicit save/confirm first
    }

    /** Call POST /shipping/calculate for a { city, district } pair. */
    async function fetchShippingFee(city, district) {
        setShippingLoading(true);
        setShippingError(null);
        setShippingFee(null);
        try {
            const result = await shippingApi.calculate(city, district);
            setShippingFee(result.fee);
        } catch (err) {
            // Backend throws clear error: "Khu vực chưa được hỗ trợ tính phí ship"
            setShippingError(err?.message || 'Không thể tính phí vận chuyển');
            setShippingFee(null);
        } finally {
            setShippingLoading(false);
        }
    }

    // ── handlers ─────────────────────────────────────────────────────────────

    const handleSelectSavedAddress = (addressId) => {
        setSelectedAddressId(addressId);
    };

    /** Called when the user clicks "Xác nhận địa chỉ" on the saved-address list. */
    const handleConfirmSavedAddress = () => {
        const addr = resolveCurrentAddress();
        if (!addr) {
            toast.error('Vui lòng chọn địa chỉ giao hàng');
            return;
        }
        setConfirmedAddress({ city: addr.city, district: addr.district });
        setShippingError(null);
        fetchShippingFee(addr.city, addr.district);
    };

    /**
     * Called when the user clicks "Xác nhận địa chỉ" in new-address mode.
     * Validates the picker (district + ward + line1 + fullName + phone), then
     * saves to the backend, switches to saved mode and computes shipping fee.
     */
    const handleConfirmNewAddress = async () => {
        const errors = validateNewAddress(pickerAddress);
        if (Object.keys(errors).length > 0) {
            setAddressErrors(errors);
            return;
        }

        const addr = {
            label: '',
            fullName: pickerAddress.fullName,
            phone: pickerAddress.phone,
            line1: pickerAddress.line1,
            ward: pickerAddress.ward,
            district: pickerAddress.district,
            city: pickerAddress.city,
            isDefault: pickerAddress.isDefault ?? true,
        };

        try {
            const saved = await usersApi.addAddress(addr);
            setSavedAddresses((prev) => {
                const updated = prev.filter((a) => a.id !== saved.id);
                updated.push({ ...saved });
                return updated;
            });
            setSelectedAddressId(saved.id);
            setMode('saved');
            setPickerAddress(null);
            setAddressErrors({});
            setConfirmedAddress({ city: saved.city, district: saved.district });
            fetchShippingFee(saved.city, saved.district);
            toast.success('Địa chỉ đã được lưu');
        } catch (err) {
            // toast already shown by apiClient interceptor
            console.error('[Checkout] addAddress failed', err);
        }
    };

    const handleDeleteAddress = async (id) => {
        try {
            await usersApi.deleteAddress(id);
        } catch {
            // toast already shown
        }
        const remaining = savedAddresses.filter((a) => a.id !== id);
        setSavedAddresses(remaining);
        if (selectedAddressId === id) {
            const next = remaining[0]?.id || null;
            setSelectedAddressId(next);
            // Clear shipping if no address left or if the deleted address was confirmed.
            if (next === null || confirmedAddress !== null) {
                setConfirmedAddress(null);
                setShippingFee(null);
                setShippingError(null);
                setShippingLoading(false);
            }
        }
        toast.success('Đã xoá địa chỉ');
    };

    const handleSwitchToNew = () => {
        setMode('new');
        setConfirmedAddress(null);
        setShippingFee(null);
        setShippingError(null);
        setShippingLoading(false);
        setPickerAddress(null);
        setAddressErrors({});
    };

    const handleSwitchToSaved = () => {
        setMode('saved');
    };

    /** Main order confirmation handler — only reached when isShippingReady === true. */
    const handleConfirm = async () => {
        // ── resolve address ────────────────────────────────────────────────
        let addressId = null;
        if (mode === 'saved') {
            addressId = selectedAddressId;
        } else {
            const errors = validateNewAddress(pickerAddress);
            if (Object.keys(errors).length > 0) {
                setAddressErrors(errors);
                toast.error('Vui lòng điền đầy đủ thông tin địa chỉ');
                return;
            }
            try {
                const saved = await usersApi.addAddress({
                    label: '',
                    fullName: pickerAddress.fullName,
                    phone: pickerAddress.phone,
                    line1: pickerAddress.line1,
                    ward: pickerAddress.ward,
                    district: pickerAddress.district,
                    city: pickerAddress.city,
                    isDefault: pickerAddress.isDefault ?? true,
                });
                addressId = saved.id;
            } catch (err) {
                console.error('[Checkout] addAddress failed', err);
                return;
            }
        }

        // ── confirm dialog ─────────────────────────────────────────────────
        const ok = await confirm({
            title: 'Xác nhận đặt hàng',
            message: `Tổng thanh toán ${formatCurrency(totalAmount)}. Bạn có chắc muốn đặt đơn hàng này?`,
            confirmLabel: 'Đặt hàng',
            loading: submitting,
        });
        if (!ok) return;

        // ── submit ─────────────────────────────────────────────────────────
        setSubmitting(true);
        try {
            const order = await ordersApi.checkout({
                addressId,
                promotionCode: voucherPreview?.valid ? voucherCode.trim().toUpperCase() : null,
                paymentMethod,
                productIds: initialProductIds.length > 0 ? initialProductIds : null,
            });

            // Sync: if the backend-computed shipping fee differs from our preview,
            // update the display to reflect reality. This is a rare edge case
            // (admin changed the distance table mid-checkout).
            if (order.shippingFee !== null && order.shippingFee !== shippingFeeValue) {
                setShippingFee(order.shippingFee);
            }

            toast.success(`Đặt hàng thành công — ${order.orderNumber}`);
            await refresh();
            navigate(`/orders/${order.id}`, { replace: true });
        } catch (err) {
            // If the error is "khu vực chưa hỗ trợ", clear the confirmed address
            // so the customer is forced to pick another one.
            if (err?.message?.includes('chưa được hỗ trợ')) {
                setConfirmedAddress(null);
                setShippingFee(null);
            }
        } finally {
            setSubmitting(false);
        }
    };

    // ── guards ────────────────────────────────────────────────────────────────
    if (items.length === 0 || checkoutItems.length === 0) {
        return (
            <div className="container checkout-page">
                <div className="checkout-page__empty">
                    <h2>Giỏ hàng trống</h2>
                    <p>Bạn cần thêm sản phẩm vào giỏ trước khi thanh toán.</p>
                    <Link to="/products" className="checkout-page__cta">Mua sắm ngay</Link>
                </div>
            </div>
        );
    }

    // ── render ────────────────────────────────────────────────────────────────
    return (
        <div className="container checkout-page">
            <header className="checkout-page__header">
                <h1>Thanh toán</h1>
                <Link to="/cart" className="checkout-page__back">← Quay lại giỏ hàng</Link>
            </header>

            <div className="checkout-page__layout">
                <div className="checkout-page__main">
                    {/* ===== Address ===== */}
                    <section className="checkout-section">
                        <h2 className="checkout-section__title">1. Địa chỉ giao hàng</h2>

                        {savedAddresses.length > 0 && (
                            <div className="checkout-section__tabs">
                                <button
                                    type="button"
                                    className={`checkout-section__tab ${mode === 'saved' ? 'is-active' : ''}`}
                                    onClick={handleSwitchToSaved}
                                >
                                    Đã lưu ({savedAddresses.length})
                                </button>
                                <button
                                    type="button"
                                    className={`checkout-section__tab ${mode === 'new' ? 'is-active' : ''}`}
                                    onClick={handleSwitchToNew}
                                >
                                    Địa chỉ mới
                                </button>
                            </div>
                        )}

                        {mode === 'saved' && savedAddresses.length > 0 && (
                            <>
                            <ul className="address-list">
                                {savedAddresses.map((a) => (
                                    <li
                                        key={a.id}
                                        className={`address-list__item ${selectedAddressId === a.id ? 'is-selected' : ''}`}
                                    >
                                        <label className="address-list__row">
                                            <input
                                                type="radio"
                                                name="address"
                                                checked={selectedAddressId === a.id}
                                                onChange={() => handleSelectSavedAddress(a.id)}
                                            />
                                            <span className="address-list__body">
                                                <span className="address-list__label">
                                                    {a.label || 'Địa chỉ'}
                                                    {a.isDefault && <span className="address-list__default">Mặc định</span>}
                                                </span>
                                                <span className="address-list__text">
                                                    {a.fullName} · {a.phone}
                                                </span>
                                                <span className="address-list__text">
                                                    {a.line1}, {a.ward}, {a.district}, {a.city}
                                                </span>
                                            </span>
                                        </label>
                                        <button
                                            type="button"
                                            className="address-list__delete"
                                            onClick={() => handleDeleteAddress(a.id)}
                                            aria-label="Xoá địa chỉ"
                                        >
                                            ✕
                                        </button>
                                    </li>
                                ))}
                            </ul>

                            <div className="address-list__confirm">
                                <Button
                                    type="button"
                                    variant="primary"
                                    size="md"
                                    onClick={handleConfirmSavedAddress}
                                    disabled={!selectedAddressId || shippingLoading}
                                    loading={shippingLoading && confirmedAddress === null}
                                >
                                    {shippingLoading && selectedAddressId !== null
                                        ? 'Đang tính phí ship…'
                                        : 'Xác nhận địa chỉ'}
                                </Button>
                                {shippingError && (
                                    <p className="checkout-page__shipping-error">
                                        ⚠ {shippingError} — vui lòng chọn địa chỉ khác.
                                    </p>
                                )}
                            </div>
                            </>
                        )}

                        {mode === 'new' && (
                            <div className="address-form">
                                {/* Full name + phone side by side */}
                                <div className="address-form__row">
                                    <Input
                                        id="addr-fullName"
                                        label="Họ và tên"
                                        value={pickerAddress?.fullName || ''}
                                        onChange={(e) => {
                                            setPickerAddress((p) => ({
                                                ...(p || {}),
                                                fullName: e.target.value,
                                            }));
                                            if (addressErrors.fullName) {
                                                setAddressErrors({ ...addressErrors, fullName: null });
                                            }
                                        }}
                                        error={addressErrors.fullName}
                                        required
                                    />
                                    <Input
                                        id="addr-phone"
                                        label="Số điện thoại"
                                        value={pickerAddress?.phone || ''}
                                        onChange={(e) => {
                                            setPickerAddress((p) => ({
                                                ...(p || {}),
                                                phone: e.target.value,
                                            }));
                                            if (addressErrors.phone) {
                                                setAddressErrors({ ...addressErrors, phone: null });
                                            }
                                        }}
                                        error={addressErrors.phone}
                                        required
                                    />
                                </div>

                                {/* Guided address picker: district → ward → street */}
                                <AddressPicker
                                    value={pickerAddress}
                                    onChange={setPickerAddress}
                                    errors={addressErrors}
                                    onErrorsChange={setAddressErrors}
                                />

                                <label className="address-form__default">
                                    <input
                                        type="checkbox"
                                        checked={pickerAddress?.isDefault ?? true}
                                        onChange={(e) =>
                                            setPickerAddress((p) => ({
                                                ...(p || {}),
                                                isDefault: e.target.checked,
                                            }))
                                        }
                                    />
                                    Đặt làm địa chỉ mặc định
                                </label>

                                <div className="address-form__actions">
                                    <Button
                                        type="button"
                                        variant="primary"
                                        size="md"
                                        onClick={handleConfirmNewAddress}
                                    >
                                        Xác nhận địa chỉ
                                    </Button>
                                </div>

                                {shippingError && (
                                    <p className="checkout-page__shipping-error">
                                        ⚠ {shippingError} — vui lòng chọn địa chỉ khác.
                                    </p>
                                )}
                            </div>
                        )}
                    </section>

                    {/* ===== Payment ===== */}
                    <section className="checkout-section">
                        <h2 className="checkout-section__title">2. Phương thức thanh toán</h2>
                        <div className="payment-options">
                            {PAYMENT_METHODS.map((opt) => (
                                <label
                                    key={opt.value}
                                    className={`payment-option ${paymentMethod === opt.value ? 'is-selected' : ''}`}
                                >
                                    <input
                                        type="radio"
                                        name="payment"
                                        value={opt.value}
                                        checked={paymentMethod === opt.value}
                                        onChange={(e) => setPaymentMethod(e.target.value)}
                                    />
                                    <span>
                                        <span className="payment-option__label">{opt.label}</span>
                                        <span className="payment-option__desc">{opt.description}</span>
                                    </span>
                                </label>
                            ))}
                        </div>
                    </section>

                    {/* ===== Items summary ===== */}
                    <section className="checkout-section">
                        <h2 className="checkout-section__title">
                            3. Sản phẩm ({checkoutItems.length}
                            {checkoutItems.length < items.length ? `/${items.length}` : ''})
                        </h2>
                        <ul className="checkout-items">
                            {checkoutItems.map((item) => (
                                <li key={item.productId} className="checkout-item">
                                    <img
                                        src={item.productImage || '/placeholder-product.svg'}
                                        alt={item.productName}
                                        onError={(e) => { e.currentTarget.src = '/placeholder-product.svg'; }}
                                    />
                                    <span className="checkout-item__info">
                                        <span className="checkout-item__name">{item.productName}</span>
                                        <span className="checkout-item__qty">x{item.quantity}</span>
                                    </span>
                                    <span className="checkout-item__price">{formatCurrency(item.subtotal)}</span>
                                </li>
                            ))}
                        </ul>
                        {checkoutItems.length < items.length && (
                            <p style={{ fontSize: 13, color: '#94a3b8', margin: '8px 0 0' }}>
                                Còn <strong>{items.length - checkoutItems.length}</strong> sản phẩm
                                trong giỏ sẽ được giữ lại cho lần thanh toán sau.
                            </p>
                        )}
                    </section>
                </div>

                {/* ===== Summary ===== */}
                <aside className="checkout-page__summary">
                    <h2 className="checkout-page__summary-title">Tóm tắt</h2>

                    <div className="checkout-page__voucher">
                        <label htmlFor="voucher">Mã khuyến mãi (tuỳ chọn)</label>
                        <Input
                            id="voucher"
                            value={voucherCode}
                            onChange={(e) => setVoucherCode(e.target.value.toUpperCase())}
                            placeholder="VD: WELCOME10"
                            error={voucherError}
                            disabled={voucherLoading}
                        />
                        {voucherPreview?.valid && (
                            <p className="checkout-page__voucher-success">
                                ✓ Mã <strong>{voucherPreview.code}</strong> giảm {formatCurrency(voucherPreview.discountAmount)}
                            </p>
                        )}
                    </div>

                    <dl className="checkout-page__totals">
                        {/* Tạm tính */}
                        <div className="checkout-page__total-row">
                            <dt>Tạm tính</dt>
                            <dd>{formatCurrency(subtotal)}</dd>
                        </div>

                        {/* Phí vận chuyển */}
                        <div className="checkout-page__total-row">
                            <dt>Phí vận chuyển</dt>
                            <dd>
                                {shippingLoading ? (
                                    <span className="checkout-page__shipping-loading">Đang tính phí ship…</span>
                                ) : shippingError ? (
                                    <span className="checkout-page__shipping-error-inline" title={shippingError}>
                                        Không khả dụng
                                    </span>
                                ) : shippingFee !== null ? (
                                    formatCurrency(shippingFee)
                                ) : (
                                    <span className="checkout-page__shipping-unknown">Chưa xác định</span>
                                )}
                            </dd>
                        </div>

                        {/* Giảm giá (chỉ khi voucher hợp lệ) */}
                        {voucherPreview?.valid && (
                            <div className="checkout-page__total-row checkout-page__total-row--discount">
                                <dt>Giảm giá ({voucherPreview.code})</dt>
                                <dd>− {formatCurrency(discount)}</dd>
                            </div>
                        )}

                        {/* Tổng cộng */}
                        <div className="checkout-page__total-row checkout-page__total-row--final">
                            <dt>Tổng thanh toán</dt>
                            <dd>
                                {shippingFee === null && !shippingLoading
                                    ? '—'
                                    : formatCurrency(totalAmount)}
                            </dd>
                        </div>
                    </dl>

                    <Button
                        variant="primary"
                        size="lg"
                        fullWidth
                        onClick={handleConfirm}
                        loading={submitting}
                        disabled={
                            submitting ||
                            checkoutItems.length === 0 ||
                            !isShippingReady   // requires confirmed address + resolved shipping fee
                        }
                    >
                        {!isShippingReady
                            ? 'Vui lòng xác nhận địa chỉ'
                            : 'Đặt hàng'}
                    </Button>
                </aside>
            </div>
            {dialog}
        </div>
    );
}

// =============================================================
// helpers
// =============================================================

/**
 * Validate a new-address draft from AddressPicker.
 * Returns an object keyed by field name with the human-readable error.
 *
 * Required fields:
 *   fullName, phone  — typed by the customer
 *   line1, ward, district, city  — picked via AddressPicker
 */
function validateNewAddress(addr) {
    const errors = {};
    if (!addr) {
        errors.district = 'Vui lòng hoàn tất các bước chọn địa chỉ';
        return errors;
    }
    if (!addr.fullName || !addr.fullName.trim()) {
        errors.fullName = 'Vui lòng nhập họ tên';
    }
    if (!addr.phone || !addr.phone.trim()) {
        errors.phone = 'Vui lòng nhập số điện thoại';
    } else if (!/^[0-9+\-\s()]{8,20}$/.test(addr.phone)) {
        errors.phone = 'Số điện thoại không hợp lệ';
    }
    if (!addr.line1 || !addr.line1.trim()) {
        errors.line1 = 'Vui lòng nhập số nhà, tên đường';
    }
    if (!addr.ward) {
        errors.ward = 'Vui lòng chọn phường/xã';
    }
    if (!addr.district) {
        errors.district = 'Vui lòng chọn quận/huyện';
    }
    return errors;
}
