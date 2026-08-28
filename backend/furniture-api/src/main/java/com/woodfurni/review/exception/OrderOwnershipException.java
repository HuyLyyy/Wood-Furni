package com.woodfurni.review.exception;

/**
 * Thrown when the authenticated user attempts to access
 * an order that does not belong to them.
 * → HTTP 403 Forbidden
 */
public class OrderOwnershipException extends RuntimeException {

    public OrderOwnershipException() {
        super("Order does not belong to this user");
    }

    public OrderOwnershipException(String message) {
        super(message);
    }
}
