package com.woodfurni.order.enums;

/**
 * Order status state machine:
 *
 * PENDING → CONFIRMED → PROCESSING → SHIPPING → DELIVERED
 *     │
 *     └─────────────────────→ CANCELLED (from PENDING or CONFIRMED only)
 *
 * DELIVERED → RETURNED (within return window, optional)
 *
 * State transition rules are enforced in OrderService.updateStatus().
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPING,
    DELIVERED,
    CANCELLED,
    RETURNED
}
