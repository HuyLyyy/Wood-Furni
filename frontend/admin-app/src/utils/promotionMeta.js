/**
 * Promotion metadata for badges / type filters.
 * Backend enums:
 *   - com.woodfurni.promotion.enums.PromotionType  (PERCENTAGE, FIXED_AMOUNT)
 *   - com.woodfurni.promotion.enums.PromotionStatus (ACTIVE, EXPIRED, DISABLED)
 */

export const PROMOTION_TYPE = [
    { value: 'PERCENTAGE',   label: 'Phần trăm (%)' },
    { value: 'FIXED_AMOUNT', label: 'Số tiền cố định' },
];

export const PROMOTION_STATUS = [
    { value: 'ACTIVE',   label: 'Đang chạy',  tone: 'ok' },
    { value: 'EXPIRED',  label: 'Hết hạn',    tone: 'mute' },
    { value: 'DISABLED', label: 'Đã tắt',     tone: 'danger' },
];

export function promotionTypeLabel(value) {
    return PROMOTION_TYPE.find((t) => t.value === value)?.label ?? value;
}

export function promotionStatusLabel(value) {
    return PROMOTION_STATUS.find((s) => s.value === value)?.label ?? value;
}

export function promotionStatusTone(value) {
    return PROMOTION_STATUS.find((s) => s.value === value)?.tone ?? 'mute';
}

/**
 * Returns true if a promotion with the given dates+status is currently
 * usable (in its date window). Backend PromotionService validates this
 * server-side; this is for UX badge only.
 */
export function isPromotionActiveNow(startDate, endDate, status) {
    if (status !== 'ACTIVE') return false;
    const now = Date.now();
    const start = startDate ? new Date(startDate).getTime() : -Infinity;
    const end = endDate ? new Date(endDate).getTime() : Infinity;
    return now >= start && now <= end;
}
