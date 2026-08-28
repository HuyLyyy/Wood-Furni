package com.woodfurni.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payload posted by Spring Boot to gateway:
 *   POST /internal/notify/order-created
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedPayload {
    private String orderId;
    private String orderNumber;
    private BigDecimal totalAmount;
}