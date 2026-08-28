package com.woodfurni.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload posted by Spring Boot to gateway:
 *   POST /internal/notify/order-status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusPayload {
    private String orderId;
    private String orderNumber;
    private String status;
    private String customerId;
}