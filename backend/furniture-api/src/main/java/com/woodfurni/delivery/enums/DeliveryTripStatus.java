package com.woodfurni.delivery.enums;

/**
 * Trạng thái chuyến xe.
 *
 * PLANNING   → vừa tạo, đơn hàng đã gán nhưng tài xế chưa bắt đầu
 * SHIPPING   → tài xế đã xuất phát, đang trên đường giao
 * ARRIVED    → tài xế đã đến địa điểm giao hàng (chờ gọi khách)
 * RESCHEDULED→ có đơn trong chuyến bị hoãn, đang chờ giao lại (chuyến
 *              vẫn có thể tiếp tục giao các đơn khác; nếu TẤT CẢ đơn đều
 *              bị hoãn thì chuyến tự động chuyển sang RESCHEDULED).
 * COMPLETED  → tất cả đơn đã xử lý xong (giao thành công, hoãn, hoặc fail)
 * CANCELLED  → chuyến xe bị hủy trước khi giao
 */
public enum DeliveryTripStatus {
    PLANNING,
    SHIPPING,
    ARRIVED,
    RESCHEDULED,
    COMPLETED,
    CANCELLED
}
