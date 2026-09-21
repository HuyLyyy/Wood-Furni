package com.woodfurni.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Daily revenue data point for the "revenue by day" chart, used when the
 * dashboard zooms into a single month.
 *
 * `date` is ISO yyyy-MM-dd.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyRevenueResponse {
    private String date;      // "2026-09-15"
    private BigDecimal revenue;
}
