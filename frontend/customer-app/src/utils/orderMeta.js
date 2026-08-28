/**
 * Order & payment enums (mirror backend exactly).
 */

export const ORDER_STATUS = [
    { value: 'PENDING', label: 'Chờ xác nhận', color: 'pending' },
    { value: 'CONFIRMED', label: 'Đã xác nhận', color: 'confirmed' },
    { value: 'PROCESSING', label: 'Đang xử lý', color: 'processing' },
    { value: 'SHIPPING', label: 'Đang giao', color: 'shipping' },
    { value: 'DELIVERED', label: 'Đã giao', color: 'delivered' },
    { value: 'CANCELLED', label: 'Đã huỷ', color: 'cancelled' },
    { value: 'RETURNED', label: 'Đã trả', color: 'returned' },
];

export const STATUSES_FOR_FILTER = [
    { value: '', label: 'Tất cả' },
    { value: 'PENDING', label: 'Chờ xác nhận' },
    { value: 'CONFIRMED', label: 'Đã xác nhận' },
    { value: 'PROCESSING', label: 'Đang xử lý' },
    { value: 'SHIPPING', label: 'Đang giao' },
    { value: 'DELIVERED', label: 'Đã giao' },
    { value: 'CANCELLED', label: 'Đã huỷ' },
];

export const PAYMENT_METHODS = [
    { value: 'COD', label: 'Thanh toán khi nhận hàng (COD)', description: 'Bạn sẽ trả tiền khi nhận được hàng' },
    { value: 'SANDBOX_CARD', label: 'Thẻ tín dụng (Sandbox)', description: 'Mô phỏng — thanh toán tự động thành công' },
    { value: 'SANDBOX_WALLET', label: 'Ví điện tử (Sandbox)', description: 'Mô phỏng — thanh toán tự động thành công' },
];

export const PAYMENT_STATUS = {
    PENDING: { label: 'Chờ thanh toán', color: 'pending' },
    PAID: { label: 'Đã thanh toán', color: 'paid' },
    FAILED: { label: 'Thanh toán thất bại', color: 'failed' },
    REFUNDED: { label: 'Đã hoàn tiền', color: 'refunded' },
};

export function statusLabel(value) {
    return ORDER_STATUS.find((s) => s.value === value)?.label ?? value;
}

export function statusColor(value) {
    return ORDER_STATUS.find((s) => s.value === value)?.color ?? 'pending';
}

export function isCancellable(status) {
    return status === 'PENDING' || status === 'CONFIRMED';
}

export function isReviewable(status) {
    return status === 'DELIVERED';
}

export const ORDER_STATUS_LIST = ORDER_STATUS.map((s) => s.value);