package com.woodfurni.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Summary metrics for the admin dashboard.
 * All values computed via MongoDB aggregation pipelines.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    private BigDecimal revenueToday;
    private Long ordersToday;
    private Long newCustomersToday;
    private Long lowStockCount;
}
