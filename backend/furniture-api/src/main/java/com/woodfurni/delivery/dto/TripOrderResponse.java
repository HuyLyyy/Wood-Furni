package com.woodfurni.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Một dòng trong bảng đơn của chuyến xe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripOrderResponse {
    private String orderId;
    private String orderNumber;
    private String customerName;
    private String customerPhone;
    private String shippingCity;
    private String shippingDistrict;
    private String shippingAddressLine;
    private int sequence;
    private int itemCount;
    private double weightKg;
    private double volumeM3;
    private double lengthCm;
    private double widthCm;
    private double heightCm;
    private String orderStatus;

    // ─── Per-order delivery state (luồng hoãn giao) ────────────────────────

    /** Trạng thái giao riêng của đơn trong chuyến: PENDING/DELIVERED/RESCHEDULED/FAILED. */
    private String deliveryState;

    /** Lý do hoãn / fail nếu có. */
    private String rescheduleReason;

    /** Ghi chú của tài xế. */
    private String deliveryNote;

    /** Số lần đơn này đã bị hoãn. */
    private int rescheduleCount;

    /** Ngày giao lại dự kiến. */
    private Instant nextDeliveryDate;

    /** Thời điểm xử lý lần cuối. */
    private Instant deliveredAt;

    private Instant orderCreatedAt;
}
