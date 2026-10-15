package com.woodfurni.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO trả về sau khi tài xế xử lý 1 đơn trong chuyến (deliver / reschedule /
 * fail). Chứa trạng thái mới của cả chuyến + cảnh báo nếu reschedule lần 3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryActionResult {

    /** Trạng thái mới của chuyến sau khi xử lý đơn. */
    private String tripStatus;

    /** Trạng thái mới của đơn hàng. */
    private String orderStatus;

    /** Cảnh báo — true nếu đơn vừa bị hoãn lần thứ 3. */
    private boolean warning;

    /** Nội dung cảnh báo (nếu có). */
    private String warningMessage;

    /** Tổng số đơn trong chuyến (sau xử lý). */
    private int totalOrders;

    /** Số đơn đã giao thành công. */
    private int deliveredCount;

    /** Số đơn đang chờ giao lại. */
    private int rescheduledCount;

    /** Số đơn giao thất bại. */
    private int failedCount;
}
