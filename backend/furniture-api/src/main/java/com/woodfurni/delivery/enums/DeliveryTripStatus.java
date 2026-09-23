package com.woodfurni.delivery.enums;

/**
 * Trạng thái chuyến xe.
 *
 * PLANNING   → vừa tạo, đơn hàng đã gán nhưng tài xế chưa bắt đầu
 * SHIPPING   → tài xế đã xuất phát, đang giao
 * COMPLETED  → tất cả đơn đã giao xong (Sales/Admin xác nhận)
 * CANCELLED  → chuyến xe bị hủy
 */
public enum DeliveryTripStatus {
    PLANNING,
    SHIPPING,
    COMPLETED,
    CANCELLED
}
