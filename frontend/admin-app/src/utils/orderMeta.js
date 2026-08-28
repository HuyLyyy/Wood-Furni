/**
 * Order status metadata for status badge labels.
 * Mirrors customer-app's orderMeta — duplicated intentionally so the two
 * apps stay self-contained.
 */

export const ORDER_STATUS = [
    { value: 'PENDING',    label: 'Chờ xác nhận' },
    { value: 'CONFIRMED',  label: 'Đã xác nhận' },
    { value: 'PROCESSING', label: 'Đang xử lý' },
    { value: 'SHIPPING',   label: 'Đang giao' },
    { value: 'DELIVERED',  label: 'Đã giao' },
    { value: 'CANCELLED',  label: 'Đã huỷ' },
    { value: 'RETURNED',   label: 'Đã trả' },
];

export const PAYMENT_STATUS = [
    { value: 'UNPAID',   label: 'Chưa thanh toán', tone: 'warn' },
    { value: 'PAID',     label: 'Đã thanh toán',   tone: 'ok' },
    { value: 'FAILED',   label: 'Thất bại',        tone: 'danger' },
    { value: 'REFUNDED', label: 'Đã hoàn tiền',    tone: 'mute' },
];

export const PAYMENT_METHOD = [
    { value: 'COD',            label: 'COD' },
    { value: 'SANDBOX_CARD',   label: 'Sandbox Card' },
    { value: 'SANDBOX_WALLET', label: 'Sandbox Wallet' },
];

export function statusLabel(value) {
    return ORDER_STATUS.find((s) => s.value === value)?.label ?? value;
}

export function paymentStatusLabel(value) {
    return PAYMENT_STATUS.find((s) => s.value === value)?.label ?? value;
}

export function paymentMethodLabel(value) {
    return PAYMENT_METHOD.find((s) => s.value === value)?.label ?? value;
}

/**
 * Order status colour for the badge — used by OrderListPage.
 */
export function statusTone(value) {
    switch (value) {
        case 'PENDING':    return 'warn';
        case 'CONFIRMED':  return 'info';
        case 'PROCESSING': return 'info';
        case 'SHIPPING':   return 'info';
        case 'DELIVERED':  return 'ok';
        case 'CANCELLED':  return 'danger';
        case 'RETURNED':   return 'mute';
        default: return 'mute';
    }
}

/**
 * Order status state machine — mirrors OrderService.isValidTransition() on
 * the backend (com.woodfurni.order.service.OrderService.updateStatus).
 *
 * Returns the Set of statuses that can follow `current`. UI uses this to
 * decide which actions to show in the dropdown.
 *
 * Spec (Mục 4.3):
 *   PENDING    → { CONFIRMED, CANCELLED }
 *   CONFIRMED  → { PROCESSING, CANCELLED }
 *   PROCESSING → { SHIPPING }
 *   SHIPPING   → { DELIVERED }
 *   DELIVERED  → {}   // terminal — final state, no further transitions
 *   CANCELLED  → {}   // terminal
 *   RETURNED   → {}   // terminal (reserved for future use)
 */
export const NEXT_STATUSES = {
    PENDING:    ['CONFIRMED', 'CANCELLED'],
    CONFIRMED:  ['PROCESSING', 'CANCELLED'],
    PROCESSING: ['SHIPPING'],
    SHIPPING:   ['DELIVERED'],
    DELIVERED:  [],
    CANCELLED:  [],
    RETURNED:   [],
};

export function nextStatuses(current) {
    return NEXT_STATUSES[current] ?? [];
}
