package com.woodfurni.delivery.enums;

/**
 * Lý do hoãn giao hàng theo luồng nghiệp vụ.
 *
 * - BUSY            : Khách có việc bận đột xuất
 * - NOT_READY       : Khách chưa chuẩn bị không gian
 * - REQUEST_TIME    : Khách yêu cầu giao giờ khác
 * - INACCESSIBLE    : Địa chỉ không tiếp cận được
 * - UNREACHABLE     : Không liên lạc được (sau khi đã chờ 15–30 phút)
 * - OTHER           : Lý do khác — bắt buộc nhập note
 */
public enum RescheduleReason {
    BUSY,
    NOT_READY,
    REQUEST_TIME,
    INACCESSIBLE,
    UNREACHABLE,
    OTHER
}
