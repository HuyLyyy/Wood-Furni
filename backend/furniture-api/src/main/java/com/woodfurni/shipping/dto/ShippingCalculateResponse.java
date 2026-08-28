package com.woodfurni.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response from POST /shipping/calculate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingCalculateResponse {

    /**
     * Final shipping fee in VND.
     */
    private BigDecimal fee;

    /**
     * Estimated distance used in the calculation (km).
     */
    private BigDecimal distanceKm;

    /**
     * True when the city is outside "Hồ Chí Minh"; the distance is the
     * configured fallback value rather than a table lookup.
     */
    private Boolean isOutOfProvince;
}
