package com.woodfurni.order.model;

import com.woodfurni.order.enums.PaymentMethod;
import com.woodfurni.order.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment entity — collection: "payments"
 *
 * Payment is created synchronously during checkout.
 * - COD: status starts as PENDING, upgraded to PAID when order is confirmed
 * - SANDBOX_CARD / SANDBOX_WALLET: immediately set to SUCCESS (sandbox simulation)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payments")
public class Payment {

    @Id
    private String id;

    @Indexed
    private String orderId;

    private PaymentMethod method;

    private BigDecimal amount;

    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    private String transactionRef;

    private Instant paidAt;

    @CreatedDate
    private Instant createdAt;
}
