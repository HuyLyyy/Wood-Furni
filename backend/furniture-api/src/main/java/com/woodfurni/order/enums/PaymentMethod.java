package com.woodfurni.order.enums;

/**
 * Payment method.
 *
 * - COD: Cash on Delivery (no upfront payment)
 * - SANDBOX_CARD: Simulated card payment (sandbox environment)
 * - SANDBOX_WALLET: Simulated wallet payment (sandbox environment)
 *
 * All non-COD methods are auto-confirmed as SUCCESS in sandbox.
 */
public enum PaymentMethod {
    COD,
    SANDBOX_CARD,
    SANDBOX_WALLET
}
