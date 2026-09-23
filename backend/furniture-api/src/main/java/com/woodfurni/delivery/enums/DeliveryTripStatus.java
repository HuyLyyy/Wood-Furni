package com.woodfurni.delivery.enums;

/**
 * Trạng thái chuyến xe.
 *
 * PLANNING  → vừa tạo, đơn hàng đã gán nhưng tài xế chưa bắt đầu
 * SHIPPING  → tài xế đã xuất phát, đang giao
 * COMPLETED → tất cả đơn đã DELIVERED / CANCELLED, đóng chuyến
 */
public enum DeliveryTripStatus {
    PLANNING,
    SHIPPING,
    COMPLETED
}
