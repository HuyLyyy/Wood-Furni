package com.woodfurni.reporting.controller;

import com.woodfurni.common.ApiResponse;
import com.woodfurni.reporting.dto.*;
import com.woodfurni.reporting.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    private final MongoTemplate mongoTemplate;

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
               description = "Aggregated revenue by month. " +
                       "If year + month are provided, returns daily revenue for that month " +
                       "(yyyy-MM-dd keys, zero-filled). Otherwise returns last 12 months " +
                       "(yyyy-MM keys).")
    public ResponseEntity<ApiResponse<?>> getMonthlyRevenue(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        if (year != null && month != null) {
            List<DailyRevenueResponse> daily = reportingService.getDailyRevenue(year, month);
            return ResponseEntity.ok(ApiResponse.success(daily));
        }
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

    // ============================================================
    // DEBUG: dump orders in ICT today so we can inspect what the
    // aggregation actually sees. Temporary — remove after fix is verified.
    // ============================================================
    @GetMapping("/_debug/orders-today")
    public ResponseEntity<ApiResponse<List<Document>>> debugOrdersToday() {
        ZoneId ict = ZoneId.of("Asia/Ho_Chi_Minh");
        Instant start = LocalDate.now(ict).atStartOfDay(ict).toInstant();
        Instant end = start.plus(1, ChronoUnit.DAYS);
        // Window is intentionally wide: orders created in the last 7 days, so we
        // can spot whether statusHistory events land on the right day.
        Instant since = start.minus(7, ChronoUnit.DAYS);

        List<Document> rows = new ArrayList<>();
        for (Document d : mongoTemplate.find(
                new Query(Criteria.where("createdAt").gte(since)),
                Document.class, "orders")) {
            rows.add(d);
        }
        return ResponseEntity.ok(ApiResponse.success(rows));
    }
}
