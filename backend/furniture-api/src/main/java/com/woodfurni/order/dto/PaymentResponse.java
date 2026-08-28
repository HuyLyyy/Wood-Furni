package com.woodfurni.order.dto;

import com.woodfurni.order.enums.PaymentMethod;
import com.woodfurni.order.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String id;
    private String orderId;
    private PaymentMethod method;
    private BigDecimal amount;
    private PaymentStatus status;
    private String transactionRef;
    private Instant paidAt;
    private Instant createdAt;
}
