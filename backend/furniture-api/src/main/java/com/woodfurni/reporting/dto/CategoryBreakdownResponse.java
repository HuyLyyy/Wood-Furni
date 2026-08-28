package com.woodfurni.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Revenue breakdown by environment (Indoor/Outdoor) and category.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryBreakdownResponse {

    private String environment;
    private String categoryId;
    private String categoryName;
    private BigDecimal revenue;
    private Long orderCount;
}
