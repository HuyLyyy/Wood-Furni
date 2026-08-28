package com.woodfurni.shipping.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /shipping/calculate.
 *
 * Customer previews shipping fee immediately after entering their delivery address
 * (before placing the order). The endpoint does NOT need cart data — it computes
 * fee purely from the address and the pre-configured distance table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingCalculateRequest {

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "District is required")
    private String district;
}
