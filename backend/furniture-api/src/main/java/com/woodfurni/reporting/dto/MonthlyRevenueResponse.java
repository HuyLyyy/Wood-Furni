package com.woodfurni.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Monthly revenue data point for the revenue chart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyRevenueResponse {

    private String month;    // "2026-01" format
    private BigDecimal revenue;
}
