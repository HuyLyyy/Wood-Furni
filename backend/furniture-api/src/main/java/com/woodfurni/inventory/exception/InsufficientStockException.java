package com.woodfurni.inventory.exception;

/**
 * Exception thrown when an inventory reservation cannot be fulfilled
 * due to insufficient stock.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }

    public InsufficientStockException(String productId, int requested, int available) {
        super(String.format(
                "Insufficient stock for product %s: requested %d, available %d",
                productId, requested, available));
    }
}
