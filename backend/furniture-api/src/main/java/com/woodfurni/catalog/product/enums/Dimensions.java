package com.woodfurni.catalog.product.enums;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Embedded dimension object for products.
 * Units: centimeters (cm) for dimensions, kg for weight.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dimensions {

    private BigDecimal width;
    private BigDecimal height;
    private BigDecimal depth;
}
