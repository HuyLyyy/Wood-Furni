package com.woodfurni.order.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shipping address embedded in Order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddress {

    private String label;

    private String line1;

    private String ward;

    private String district;

    private String city;

    private String phone;
}
