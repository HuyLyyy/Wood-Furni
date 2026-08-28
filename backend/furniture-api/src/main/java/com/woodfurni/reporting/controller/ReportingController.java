package com.woodfurni.reporting.controller;

import com.woodfurni.common.ApiResponse;
import com.woodfurni.reporting.dto.*;
import com.woodfurni.reporting.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Reporting / Dashboard endpoints.
 * ADMIN / SALES / WAREHOUSE: full access to all metrics.
 *
 * All metrics computed server-side via MongoDB aggregation pipelines
 * (see ReportingService).
 */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SALES','WAREHOUSE')")
@Tag(name = "Admin Dashboard", description = "Reporting endpoints — ADMIN & SALES & WAREHOUSE")
public class ReportingController {

    private final ReportingService reportingService;

    // ============================================================
    // 1. Dashboard summary
    // ============================================================
    @GetMapping("/summary")
    @Operation(summary = "Dashboard summary",
               description = "Revenue today, orders today, new customers today, low stock count")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary() {
        DashboardSummaryResponse summary = reportingService.getDashboardSummary();
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    // ============================================================
    // 2. Revenue by month (last 12 months)
    // ============================================================
    @GetMapping("/revenue")
    @Operation(summary = "Monthly revenue",
               description = "Aggregated revenue by month for the last 12 months")
    public ResponseEntity<ApiResponse<List<MonthlyRevenueResponse>>> getMonthlyRevenue(
            @RequestParam(defaultValue = "month") String range) {
        // For now only "month" range is supported (12 most recent months)
        // Future: "week", "day", custom ranges
        List<MonthlyRevenueResponse> revenue = reportingService.getMonthlyRevenue();
        return ResponseEntity.ok(ApiResponse.success(revenue));
    }

    // ============================================================
    // 3. Orders by status
    // ============================================================
    @GetMapping("/orders-by-status")
    @Operation(summary = "Orders grouped by status",
               description = "Returns count of orders per status (PENDING, CONFIRMED, ...)")
    public ResponseEntity<ApiResponse<List<OrdersByStatusResponse>>> getOrdersByStatus() {
        List<OrdersByStatusResponse> data = reportingService.getOrdersByStatus();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ============================================================
    // 4. Top-selling products
    // ============================================================
    @GetMapping("/top-products")
    @Operation(summary = "Top selling products",
               description = "Aggregated from Order.items, sorted by quantity sold")
    public ResponseEntity<ApiResponse<List<TopProductResponse>>> getTopProducts(
            @RequestParam(defaultValue = "10") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));  // clamp 1-100
        List<TopProductResponse> data = reportingService.getTopProducts(safeLimit);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ============================================================
    // 5. Category breakdown
    // ============================================================
    @GetMapping("/category-breakdown")
    @Operation(summary = "Revenue by environment & category",
               description = "Joins orders→products→categories, groups by env+category")
    public ResponseEntity<ApiResponse<List<CategoryBreakdownResponse>>> getCategoryBreakdown() {
        List<CategoryBreakdownResponse> data = reportingService.getCategoryBreakdown();
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
