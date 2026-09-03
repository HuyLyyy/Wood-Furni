import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import Modal from '../../components/Modal.jsx';
import { Button } from '../../components/index.js';
import { formatCurrency } from '../../utils/format.js';

/**
 * ReceiveReturnModal
 *
 * Displays a table of order items with editable received quantities.
 * Staff can adjust quantities (0 to ordered qty) per item.
 *
 * Two scenarios:
 * 1. All quantities = 0 → "Xác nhận hủy đơn" (cancels the order)
 * 2. Any quantity > 0 → "Xác nhận đã giao" (delivers with adjusted amounts)
 *
 * PROMOTION RULE — orders with a promotion code can NOT be partially
 * accepted. Why: the promotion discount was applied to the whole
 * subtotal; if the customer only accepts some items, the discount math
 * no longer holds.
 *
 * So for promo orders the per-row number inputs are locked and we expose
 * only TWO explicit choices via dedicated buttons:
 *   - "Đã giao đủ"  → every row receives its full ordered quantity → DELIVERED
 *   - "Đã trả hết"  → every row receives 0                     → CANCELLED
 *
 * The form tracks the current selection in `quantities` so the visible
 * table mirrors what will be sent to the backend.
 *
 * Props:
 *   order    - order object with items array
 *   onClose  - called when modal is dismissed
 *   onConfirm - called with { items, note } when confirmed
 */
export default function ReceiveReturnModal({ order, onClose, onConfirm }) {
    const hasPromotion = Boolean(
        order.promotionCode && order.promotionCode.trim() !== ''
    );

    const orderedQuantities = (order.items || []).map((item) => item.quantity || 0);

    // Each row's current received quantity. For promo orders we keep this
    // either fully-populated (default "Đã giao đủ") or all-zero ("Đã trả hết")
    // — never any mix in between.
    const [quantities, setQuantities] = useState(orderedQuantities);
    const [note, setNote] = useState('');
    const [submitting, setSubmitting] = useState(false);

    // Re-sync if the order changes while the modal is open.
    useEffect(() => {
        setQuantities(orderedQuantities);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [order]);

    // ---- Promo-order helpers ----------------------------------------------

    /** Set every row to its full ordered quantity ("Đã giao đủ"). */
    const handlePromoSetAllDelivered = () => {
        setQuantities(orderedQuantities);
    };

    /** Set every row to 0 ("Đã trả hết" → order will be cancelled). */
    const handlePromoSetAllReturned = () => {
        setQuantities(orderedQuantities.map(() => 0));
    };

    // ---- Non-promo: per-row editing ---------------------------------------

    const handleQuantityChange = (index, value) => {
        const max = order.items[index].quantity || 0;
        const num = parseInt(value, 10);
        if (isNaN(num) || num < 0) {
            setQuantities((prev) => {
                const next = [...prev];
                next[index] = 0;
                return next;
            });
        } else if (num > max) {
            setQuantities((prev) => {
                const next = [...prev];
                next[index] = max;
                return next;
            });
        } else {
            setQuantities((prev) => {
                const next = [...prev];
                next[index] = num;
                return next;
            });
        }
    };

    // ---- Derived state -----------------------------------------------------

    const allZero = quantities.every((q) => q === 0);
    const allFull = quantities.every((q, i) => q === (order.items[i]?.quantity || 0));
    const totalReceived = quantities.reduce((sum, q) => sum + q, 0);

    // Promo-specific: detect a "partial" state (some rows full, some 0, some
    // in-between). The backend rejects partial receives for promo orders, so
    // we never let the user submit that — but we still show a hint.
    const isPartialMix =
        hasPromotion && !allZero && !allFull;

    let newSubtotal = 0;
    let changedItems = 0;
    for (let i = 0; i < order.items.length; i++) {
        const item = order.items[i];
        const received = quantities[i] || 0;
        newSubtotal += received * Number(item.unitPrice || 0);
        if (received !== (item.quantity || 0)) {
            changedItems++;
        }
    }

    const handleConfirm = () => {
        setSubmitting(true);
        const items = quantities.map((qty, index) => ({
            itemIndex: index,
            receivedQuantity: qty,
        }));
        onConfirm({ items, note: note.trim() || null });
    };

    // Disable confirm for promo orders if the state isn't a clean
    // "all full" or "all zero".
    const confirmDisabled =
        submitting || (totalReceived === 0 && !allZero) || (hasPromotion && isPartialMix);

    return (
        <Modal title="Nhận lại hàng từ NVGH" onClose={onClose} width={600}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <p style={{ margin: 0, color: '#64748b', fontSize: 14 }}>
                    Nhập số lượng sản phẩm khách hàng thực sự nhận được.
                    Nếu khách không nhận sản phẩm nào, đặt số lượng về 0.
                </p>

                {hasPromotion && (
                    <div
                        style={{
                            padding: '10px 12px',
                            background: '#fef3c7',
                            border: '1px solid #fcd34d',
                            borderRadius: 8,
                            fontSize: 14,
                            color: '#92400e',
                        }}
                    >
                        <strong>⚠️ Đơn hàng có mã khuyến mãi "{order.promotionCode}"</strong>
                        <div style={{ marginTop: 4 }}>
                            Vì khuyến mãi đã được áp dụng cho toàn bộ đơn, bạn{' '}
                            <strong>không thể nhận một phần</strong>. Chọn 1 trong 2:
                        </div>
                    </div>
                )}

                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
                    <thead>
                        <tr style={{ background: '#f1f5f9' }}>
                            <th style={{ padding: '8px 10px', textAlign: 'left' }}>Sản phẩm</th>
                            <th style={{ padding: '8px 10px', textAlign: 'center', width: 80 }}>SL đã đặt</th>
                            <th style={{ padding: '8px 10px', textAlign: 'center', width: 100 }}>SL nhận</th>
                            <th style={{ padding: '8px 10px', textAlign: 'right', width: 80 }}>Đơn giá</th>
                            <th style={{ padding: '8px 10px', textAlign: 'right', width: 100 }}>Thành tiền</th>
                        </tr>
                    </thead>
                    <tbody>
                        {order.items.map((item, i) => {
                            const received = quantities[i] || 0;
                            const ordered = item.quantity || 0;
                            const changed = received !== ordered;
                            return (
                                <tr
                                    key={item.productId || i}
                                    style={{
                                        borderBottom: '1px solid #e2e8f0',
                                        background: changed && !hasPromotion ? '#fef9c3' : undefined,
                                    }}
                                >
                                    <td style={{ padding: '10px 10px' }}>
                                        <div style={{ fontWeight: 500 }}>{item.productName || '—'}</div>
                                        {changed && !hasPromotion && (
                                            <div style={{ fontSize: 12, color: '#d97706' }}>
                                                ↓ {ordered - received} sản phẩm bị trả lại
                                            </div>
                                        )}
                                    </td>
                                    <td style={{ padding: '10px 10px', textAlign: 'center', color: '#94a3b8' }}>
                                        {ordered}
                                    </td>
                                    <td style={{ padding: '10px 10px', textAlign: 'center' }}>
                                        {hasPromotion ? (
                                            // Promo: read-only display, edited via the
                                            // two bulk-action buttons below the table.
                                            <span
                                                style={{
                                                    display: 'inline-block',
                                                    width: 64,
                                                    padding: '4px 8px',
                                                    border: '1px solid #cbd5e1',
                                                    borderRadius: 6,
                                                    textAlign: 'center',
                                                    fontSize: 14,
                                                    background: received === 0 ? '#fee2e2' : '#dcfce7',
                                                    color: received === 0 ? '#b91c1c' : '#166534',
                                                    fontWeight: 600,
                                                }}
                                                title={
                                                    received === 0
                                                        ? 'Đã trả — khách không nhận'
                                                        : 'Đã giao — khách nhận đủ'
                                                }
                                            >
                                                {received}
                                            </span>
                                        ) : (
                                            <input
                                                type="number"
                                                min={0}
                                                max={ordered}
                                                value={received}
                                                onChange={(e) => handleQuantityChange(i, e.target.value)}
                                                style={{
                                                    width: 64,
                                                    padding: '4px 8px',
                                                    border: '1px solid #cbd5e1',
                                                    borderRadius: 6,
                                                    textAlign: 'center',
                                                    fontSize: 14,
                                                }}
                                            />
                                        )}
                                    </td>
                                    <td style={{ padding: '10px 10px', textAlign: 'right' }}>
                                        {formatCurrency(item.unitPrice)}
                                    </td>
                                    <td style={{ padding: '10px 10px', textAlign: 'right', fontWeight: 500 }}>
                                        {formatCurrency(received * Number(item.unitPrice || 0))}
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>

                {hasPromotion && (
                    <div
                        style={{
                            padding: '10px 12px',
                            background: '#e0f2fe',
                            border: '1px solid #bae6fd',
                            borderRadius: 8,
                            fontSize: 14,
                            color: '#075985',
                        }}
                    >
                        <div style={{ marginBottom: 8 }}>
                            Trạng thái hiện tại:{' '}
                            <strong>
                                {allZero
                                    ? 'Đã trả hết — sẽ HỦY đơn, hoàn tiền & khôi phục mã KM'
                                    : allFull
                                        ? 'Đã giao đủ tất cả'
                                        : 'Hỗn hợp (không hợp lệ cho đơn có KM)'}
                            </strong>
                        </div>
                        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                            <Button
                                variant={allFull ? 'primary' : 'ghost'}
                                disabled={submitting}
                                onClick={handlePromoSetAllDelivered}
                            >
                                ✅ Đã giao đủ
                            </Button>
                            <Button
                                variant={allZero ? 'danger' : 'ghost'}
                                disabled={submitting}
                                onClick={handlePromoSetAllReturned}
                            >
                                ↩️ Đã trả hết (hủy đơn)
                            </Button>
                        </div>
                    </div>
                )}

                {!hasPromotion && changedItems > 0 && (
                    <div
                        style={{
                            padding: '10px 12px',
                            background: allZero ? '#fee2e2' : '#fef3c7',
                            borderRadius: 8,
                            fontSize: 14,
                        }}
                    >
                        {allZero ? (
                            <strong style={{ color: '#dc2626' }}>
                                ⚠️ Tất cả sản phẩm có số lượng nhận = 0.
                                Đơn hàng sẽ bị <strong>HỦY</strong> và hoàn tiền cho khách.
                            </strong>
                        ) : (
                            <span style={{ color: '#92400e' }}>
                                Đơn hàng sẽ được điều chỉnh với <strong>{changedItems}</strong> sản phẩm bị trả lại.
                                Tổng tiền mới: <strong>{formatCurrency(newSubtotal)}</strong> (chưa tính phí ship).
                            </span>
                        )}
                    </div>
                )}

                <div>
                    <label style={{ display: 'block', fontWeight: 500, marginBottom: 4, fontSize: 14 }}>
                        Ghi chú (tùy chọn)
                    </label>
                    <textarea
                        value={note}
                        onChange={(e) => setNote(e.target.value)}
                        placeholder="VD: Khách hàng từ chối nhận sản phẩm vì giao trễ..."
                        rows={2}
                        style={{
                            width: '100%',
                            padding: '8px 10px',
                            border: '1px solid #cbd5e1',
                            borderRadius: 6,
                            fontSize: 14,
                            resize: 'vertical',
                            fontFamily: 'inherit',
                        }}
                    />
                </div>

                <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', paddingTop: 8 }}>
                    <Button variant="ghost" onClick={onClose} disabled={submitting}>
                        Đóng
                    </Button>
                    <Button
                        variant={allZero ? 'danger' : 'primary'}
                        disabled={confirmDisabled}
                        onClick={handleConfirm}
                    >
                        {submitting
                            ? 'Đang xử lý...'
                            : allZero
                                ? 'Xác nhận hủy đơn'
                                : 'Xác nhận đã giao'}
                    </Button>
                </div>
            </div>
        </Modal>
    );
}

ReceiveReturnModal.propTypes = {
    order: PropTypes.object.isRequired,
    onClose: PropTypes.func.isRequired,
    onConfirm: PropTypes.func.isRequired,
};
