package com.woodfurni.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Payload for /internal/notify/order-rescheduled — luồng hoãn giao hàng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRescheduledPayload {
    private String orderId;
    private String orderNumber;
    private String customerId;
    private String reason;
    private Instant nextDeliveryDate;
    private int rescheduleCount;
    private String preferredTimeWindow;
    private String note;
}
