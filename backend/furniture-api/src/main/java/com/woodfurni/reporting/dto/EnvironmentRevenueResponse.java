package com.woodfurni.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Total revenue for a single product environment (INDOOR / OUTDOOR).
 *
 * <p>Used by the dashboard's "Indoor vs Outdoor" pie chart. The frontend
 * maps these to two slices: "Nội thất trong nhà" (INDOOR + BOTH) and
 * "Nội thất ngoài trời" (OUTDOOR). The grouping is done in the service
 * so the client never has to know about the BOTH value.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentRevenueResponse {

    /** Display label: "INDOOR" (trong nhà) or "OUTDOOR" (ngoài trời). */
    private String environment;

    /** Display label in Vietnamese for the legend. */
    private String label;

    /** Sum of (unitPrice * quantity) across all order items in this env. */
    private BigDecimal revenue;

    /** Number of order items counted (not distinct orders). */
    private Long orderCount;
}
