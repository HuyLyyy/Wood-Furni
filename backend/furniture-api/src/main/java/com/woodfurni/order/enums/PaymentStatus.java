package com.woodfurni.order.enums;

/**
 * Payment status for an order.
 *
 * The codebase uses two parallel naming conventions, kept side by side for
 * backward compatibility with existing persisted documents and API consumers:
 *
 *  - Legacy values used on Order.paymentStatus:
 *      UNPAID, PAID, FAILED, REFUNDED
 *
 *  - Payment-gateway-aligned values used on Payment.status:
 *      PENDING (COD awaits collection), SUCCESS (instant-paid sandbox),
 *      FAILED, REFUNDED
 */
public enum PaymentStatus {
    UNPAID,
    PAID,
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}