package com.woodfurni.delivery.enums;

/**
 * Trạng thái giao của từng đơn hàng trong một chuyến xe.
 * Lưu trên DeliveryTripOrder.deliveryState.
 *
 * PENDING      → đơn mới gán vào chuyến, chưa giao
 * DELIVERED    → giao thành công → order.status = DELIVERED
 * RESCHEDULED  → khách hoãn nhận → order.status = RESCHEDULED, chờ giao lại
 * FAILED       → giao thất bại (địa chỉ không tiếp cận, khách từ chối
 *                dứt khoát, ...) → order.status = RETURNED
 * UNREACHABLE  → không liên lạc được sau timeout → sẽ tự động chuyển
 *                sang FAILED sau khi tài xế xác nhận
 */
public enum OrderDeliveryState {
    PENDING,
    DELIVERED,
    RESCHEDULED,
    FAILED,
    UNREACHABLE
}
