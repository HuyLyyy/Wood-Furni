package com.woodfurni.catalog.product.dto;

import com.woodfurni.catalog.product.enums.ProductEnvironment;
import com.woodfurni.catalog.product.enums.ProductRoom;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for product search/filter parameters.
 * Passed as query parameters to GET /api/v1/products
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchRequest {

    private String keyword;

    private String category;

    private ProductEnvironment environment;

    private ProductRoom room;

    private String woodType;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    @Builder.Default
    private String sort = "-createdAt";
}
