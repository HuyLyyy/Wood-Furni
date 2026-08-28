package com.woodfurni.cart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Embedded cart line item.
 *
 * Price fields are snapshots taken at the moment the item is added to cart.
 * Cart does NOT maintain historical pricing — unit prices are refreshed
 * on every GET /cart call to reflect the latest product price (including salePrice).
 * This is by design: the cart is a temporary checkout buffer, not an order record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    private String productId;

    private String productName;

    private String productSlug;

    private String productImage;

    private BigDecimal unitPrice;

    private Integer quantity;

    private BigDecimal subtotal;

    public void calculateSubtotal() {
        if (unitPrice != null && quantity != null) {
            this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
