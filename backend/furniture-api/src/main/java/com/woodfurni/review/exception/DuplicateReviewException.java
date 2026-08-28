package com.woodfurni.review.exception;

/**
 * Thrown when a customer attempts to submit a duplicate review
 * for the same product and the same order.
 * → HTTP 409 Conflict
 */
public class DuplicateReviewException extends RuntimeException {

    public DuplicateReviewException() {
        super("You have already reviewed this product for this order");
    }
}
