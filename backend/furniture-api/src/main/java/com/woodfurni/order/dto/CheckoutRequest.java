package com.woodfurni.order.dto;

import com.woodfurni.order.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Checkout request payload.
 * Customer initiates checkout from their cart.
 *
 * Selection rule:
 *   - {@link #productIds} is OPTIONAL.
 *     - null/empty → checkout every item in the cart (current behaviour).
 *     - non-empty → checkout ONLY the listed productIds. Any cart item
 *       NOT in the list stays in the cart for a future checkout.
 *
 *   This lets the customer split a multi-vendor-style cart: pick which
 *   items to pay for now, leave the rest for later. The cart on the
 *   server is filtered post-checkout, so the response (cart shape) shows
 *   what's left.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

    @NotBlank(message = "Address ID is required")
    private String addressId;

    private String promotionCode;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    /**
     * Optional subset of cart product IDs to include in this checkout.
     * Null or empty means "checkout everything".
     */
    private List<String> productIds;
}