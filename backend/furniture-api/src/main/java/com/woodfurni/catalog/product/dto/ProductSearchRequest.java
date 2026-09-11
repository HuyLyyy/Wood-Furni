package com.woodfurni.catalog.product.dto;

import com.woodfurni.catalog.product.enums.ProductEnvironment;
import com.woodfurni.catalog.product.enums.ProductRoom;
import com.woodfurni.catalog.product.enums.ProductStatus;
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

    /**
     * Optional status filter. When set, the response must contain only
     * products whose <em>effective</em> status equals this value. Effective
     * status = the value the customer-app sees after the stock-derived
     * override in {@code ProductService.toResponseWithStock()} is applied,
     * i.e. it is consistent with the current {@code quantityOnHand}.
     *
     * <p>DB-side filtering happens directly when {@code status} is an
     * admin-intent value ({@code DRAFT} / {@code DISCONTINUED}). When the
     * requested status is stock-derived ({@code ACTIVE} /
     * {@code OUT_OF_STOCK}) the service falls back to in-memory filtering
     * after batch-loading inventory — see {@code ProductService.searchProducts}.
     */
    private ProductStatus status;

    @Builder.Default
    private String sort = "-createdAt";
}
