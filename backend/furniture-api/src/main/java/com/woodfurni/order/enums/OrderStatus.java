package com.woodfurni.order.enums;

/**
 * Order status state machine:
 *
 * PENDING → CONFIRMED → PROCESSING → SHIPPING → DELIVERED
 *                          │             │
 *                          │             └→ RESCHEDULED → SHIPPING (chuyến mới)
 *     │
 *     └─────────────────────→ CANCELLED (from PENDING or CONFIRMED only)
 *
 * SHIPPING → RETURNED (within return window, optional — giao thất bại)
 *
 * State transition rules are enforced in OrderService.updateStatus().
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPING,
    DELIVERED,
    RESCHEDULED,
    CANCELLED,
    RETURNED
}
