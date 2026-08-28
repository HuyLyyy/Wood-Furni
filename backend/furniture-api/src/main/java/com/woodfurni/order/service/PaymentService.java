package com.woodfurni.order.service;

import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.order.dto.PaymentResponse;
import com.woodfurni.order.enums.PaymentMethod;
import com.woodfurni.order.enums.PaymentStatus;
import com.woodfurni.order.model.Payment;
import com.woodfurni.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment service for sandbox simulation.
 *
 * Since this is a sandbox environment without real payment gateway integration,
 * all non-COD payments are immediately simulated as SUCCESS.
 *
 * In production, this would integrate with Stripe/VNPay/MoMo.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * Create a payment record for an order.
     *
     * Sandbox simulation rules:
     * - COD: Payment is PENDING until order is confirmed
     * - SANDBOX_CARD / SANDBOX_WALLET: Immediately set to SUCCESS
     */
    public PaymentResponse createPayment(String orderId, PaymentMethod method, BigDecimal amount) {
        PaymentStatus status;
        Instant paidAt = null;
        String transactionRef = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        if (method == PaymentMethod.COD) {
            status = PaymentStatus.PENDING;
        } else {
            status = PaymentStatus.SUCCESS;
            paidAt = Instant.now();
        }

        Payment payment = Payment.builder()
                .orderId(orderId)
                .method(method)
                .amount(amount)
                .status(status)
                .transactionRef(transactionRef)
                .paidAt(paidAt)
                .build();

        Payment saved = paymentRepository.save(payment);
        return toResponse(saved);
    }

    public PaymentResponse getByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for order: " + orderId));
        return toResponse(payment);
    }

    public void markRefunded(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found for order: " + orderId));
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .method(payment.getMethod())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .transactionRef(payment.getTransactionRef())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
