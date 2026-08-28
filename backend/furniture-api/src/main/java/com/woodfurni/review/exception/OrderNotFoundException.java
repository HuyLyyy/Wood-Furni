package com.woodfurni.review.exception;

/**
 * Thrown when a referenced order does not exist.
 * → HTTP 404 Not Found
 */
public class OrderNotFoundException extends RuntimeException {

    private final String orderId;

    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
