package com.woodfurni.customeradmin.dto;

import com.woodfurni.order.enums.OrderStatus;
import com.woodfurni.order.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Light-weight order summary returned inside CustomerDetailView so the
 * customer detail page can render the user's order history without
 * pulling full Order documents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOrderSummary {
    private String id;
    private String orderNumber;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private BigDecimal totalAmount;
    private Instant createdAt;
    /** Number of distinct line items on the order. */
    private int itemCount;
}
