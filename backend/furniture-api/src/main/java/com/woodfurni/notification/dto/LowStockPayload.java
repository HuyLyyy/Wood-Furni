package com.woodfurni.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload posted by Spring Boot to gateway:
 *   POST /internal/notify/low-stock
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockPayload {
    private String productId;
    private String productName;
    private Integer quantityOnHand;
    private Integer threshold;
}