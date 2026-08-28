package com.woodfurni.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedded address within a User document.
 * Not a separate MongoDB collection.
 *
 * Schema (from WOODFURNI spec Mục 3.3):
 * { "id": "string", "label": "string", "line1": "string", "ward": "string",
 *   "district": "string", "city": "string", "isDefault": "boolean", "phone": "string" }
 *
 * Note: `phone` is added beyond the original spec because the checkout flow
 * (Order.shippingAddress snapshot) needs the customer's phone number to
 * contact them about delivery. Without it, delivery is impossible.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    private String id;
    private String label;
    private String line1;
    private String ward;
    private String district;
    private String city;

    private String phone;

    @Builder.Default
    private boolean isDefault = false;
}
