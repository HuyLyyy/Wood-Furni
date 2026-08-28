package com.woodfurni.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-selling product data point.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopProductResponse {

    private String productId;
    private String productName;
    private Long totalQuantitySold;
}
