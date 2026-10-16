package com.woodfurni.delivery.dto;

import com.woodfurni.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response trả về 1 đơn RESCHEDULED — hiển thị trong modal "Tạo trip giao lại".
 * Khác với EligibleOrderResponse ở chỗ mang thêm thông tin hoãn giao
 * (lý do, số lần hoãn, ngày giao lại dự kiến) để admin biết context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleOrderResponse {

    /** ID của order trong MongoDB. */
    private String orderId;
    private String orderNumber;

    // ─── Customer info ─────────────────────────────────────────────────────
    private String customerName;
    private String customerPhone;
    private String shippingAddress;
    private String shippingDistrict;
    private String shippingCity;

    // ─── Order metrics ───────────────────────────────────────────────────
    private int itemCount;
    private double weightKg;
    private double volumeM3;
    private double lengthCm;
    private double widthCm;
    private double heightCm;

    // ─── Reschedule info ─────────────────────────────────────────────────
    /** Số lần đã hoãn (cộng dồn). */
    private int rescheduleCount;

    /** Lý do hoãn gần nhất. */
    private String rescheduleReason;

    /** Ghi chú giao hàng (deliveryNote từ link cũ). */
    private String deliveryNote;

    /** Ngày giao lại dự kiến đã hẹn với khách. */
    private String nextDeliveryDate;

    /** Ngày tạo đơn ban đầu. */
    private String createdAt;

    /**
     * ID của trip cũ đang hold đơn này (trong delivery_trip_orders).
     * Admin có thể thấy trip cũ để reference.
     */
    private String sourceTripId;
    private String sourceTripNumber;
}
