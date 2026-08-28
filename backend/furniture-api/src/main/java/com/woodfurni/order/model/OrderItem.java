package com.woodfurni.order.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Order line item — price snapshot at time of order placement.
 * Product details (name, sku, unitPrice) are copied from the cart at checkout time.
 * These fields are NOT updated if the product price changes later.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    private String productId;

    private String productName;

    private String sku;

    private BigDecimal unitPrice;

    /**
     * Original quantity ordered by the customer.
     */
    private Integer quantity;

    /**
     * Actual quantity received/accepted by the customer at delivery time.
     * Only populated when order is SHIPPING and staff performs "Nhận lại hàng".
     * - If equal to quantity: customer accepted all
     * - If less than quantity: customer rejected some
     * - If 0: customer rejected all → order becomes CANCELLED
     *
     * Null means not yet processed (order still in SHIPPING or earlier).
     */
    private Integer receivedQuantity;

    private BigDecimal subtotal;
}
