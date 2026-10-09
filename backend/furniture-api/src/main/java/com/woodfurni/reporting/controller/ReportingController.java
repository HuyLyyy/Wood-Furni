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

    // ============================================================
    // DEBUG: run the daily-revenue pipeline with the same window as the
    // dashboard, return the raw aggregation rows so we can see exactly
    // which orders contribute and why. Temporary.
    // ============================================================
    @GetMapping("/_debug/revenue-pipeline-today")
    public ResponseEntity<ApiResponse<List<Document>>> debugRevenuePipelineToday() {
        ZoneId ict = ZoneId.of("Asia/Ho_Chi_Minh");
        Instant start = LocalDate.now(ict).atStartOfDay(ict).toInstant();
        Instant end = start.plus(1, ChronoUnit.DAYS);

        // Same shape as ReportingService.revenueEventCriteria + 4 unwind stages,
        // but stop before the final $group so we can inspect each candidate order.
        org.bson.Document match = new org.bson.Document("$match", new org.bson.Document()
                .append("statusHistory.0", new org.bson.Document("$exists", true))
                .append("statusHistory.changedAt",
                        new org.bson.Document("$gte", start).append("$lt", end))
                .append("$and", java.util.List.of(
                        new org.bson.Document("$or", java.util.List.of(
                                new org.bson.Document("status", "DELIVERED"),
                                new org.bson.Document("paymentStatus", "PAID"),
                                new org.bson.Document("paymentStatus", "SUCCESS"))),
                        new org.bson.Document("paymentStatus",
                                new org.bson.Document("$ne", "REFUNDED"))
                )));
        var pipeline = java.util.List.of(
                match,
                new org.bson.Document("$unwind", "$statusHistory"),
                new org.bson.Document("$match", new org.bson.Document(
                        "$expr", new org.bson.Document(
                                "$in", java.util.List.of("$statusHistory.status",
                                        java.util.List.of("DELIVERED", "PAID", "SUCCESS"))))),
                new org.bson.Document("$sort",
                        new org.bson.Document("statusHistory.changedAt", -1)),
                new org.bson.Document("$group",
                        new org.bson.Document("_id", "$_id")
                                .append("revenueAt",
                                        new org.bson.Document("$first", "$statusHistory.changedAt"))
                                .append("totalAmount",
                                        new org.bson.Document("$first", "$totalAmount"))
                                .append("paymentStatus",
                                        new org.bson.Document("$first", "$paymentStatus"))
                                .append("status",
                                        new org.bson.Document("$first", "$status"))),
                new org.bson.Document("$match",
                        new org.bson.Document("revenueAt",
                                new org.bson.Document("$gte", start).append("$lt", end)))
        );

        List<Document> rows = new ArrayList<>();
        mongoTemplate.getCollection("orders").aggregate(pipeline).into(rows);
        return ResponseEntity.ok(ApiResponse.success(rows));
    }

    /**
     * Mirror of getDailyRevenue but exposed for inspection. Returns the raw
     * rows that survive the entire pipeline (i.e. what would be summed into
     * the per-day buckets). If this is empty but /_debug/revenue-pipeline-today
     * is not, the bucket-by-date stage is the culprit.
     */
    @GetMapping("/_debug/revenue-pipeline-month")
    public ResponseEntity<ApiResponse<List<Document>>> debugRevenuePipelineMonth(
            @RequestParam(defaultValue = "2026") int year,
            @RequestParam(defaultValue = "9") int month) {
        ZoneId ict = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate nextMonthFirstDay = firstDay.plusMonths(1);
        Instant startInstant = firstDay.atStartOfDay(ict).toInstant();
        Instant endInstant = nextMonthFirstDay.atStartOfDay(ict).toInstant();

        // Same $match + first 4 stages as getDailyRevenue — stop before the
        // final dateToString $group so we can see which orders survived.
        org.bson.Document match = new org.bson.Document("$match", new org.bson.Document()
                .append("statusHistory.0", new org.bson.Document("$exists", true))
                .append("statusHistory.changedAt",
                        new org.bson.Document("$gte", startInstant).append("$lt", endInstant))
                .append("$and", java.util.List.of(
                        new org.bson.Document("$or", java.util.List.of(
                                new org.bson.Document("status", "DELIVERED"),
                                new org.bson.Document("paymentStatus", "PAID"),
                                new org.bson.Document("paymentStatus", "SUCCESS"))),
                        new org.bson.Document("paymentStatus",
                                new org.bson.Document("$ne", "REFUNDED"))
                )));
        var pipeline = java.util.List.of(
                match,
                new org.bson.Document("$unwind", "$statusHistory"),
                new org.bson.Document("$match", new org.bson.Document(
                        "$expr", new org.bson.Document(
                                "$in", java.util.List.of("$statusHistory.status",
                                        java.util.List.of("DELIVERED", "PAID", "SUCCESS"))))),
                new org.bson.Document("$sort",
                        new org.bson.Document("statusHistory.changedAt", -1)),
                new org.bson.Document("$group",
                        new org.bson.Document("_id", "$_id")
                                .append("revenueAt",
                                        new org.bson.Document("$first", "$statusHistory.changedAt"))
                                .append("totalAmount",
                                        new org.bson.Document("$first", "$totalAmount"))
                                .append("paymentStatus",
                                        new org.bson.Document("$first", "$paymentStatus"))
                                .append("status",
                                        new org.bson.Document("$first", "$status"))),
                new org.bson.Document("$match",
                        new org.bson.Document("revenueAt",
                                new org.bson.Document("$gte", startInstant).append("$lt", endInstant)))
        );

        List<Document> rows = new ArrayList<>();
        mongoTemplate.getCollection("orders").aggregate(pipeline).into(rows);
        return ResponseEntity.ok(ApiResponse.success(rows));
    }

    /**
     * FULL pipeline (including the final $group-by-date) so we can confirm
     * whether $dateToString with timezone is the failing stage.
     */
    @GetMapping("/_debug/revenue-pipeline-full")
    public ResponseEntity<ApiResponse<List<Document>>> debugRevenuePipelineFull(
            @RequestParam(defaultValue = "2026") int year,
            @RequestParam(defaultValue = "9") int month) {
        ZoneId ict = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate nextMonthFirstDay = firstDay.plusMonths(1);
        Instant startInstant = firstDay.atStartOfDay(ict).toInstant();
        Instant endInstant = nextMonthFirstDay.atStartOfDay(ict).toInstant();

        org.bson.Document match = new org.bson.Document("$match", new org.bson.Document()
                .append("statusHistory.0", new org.bson.Document("$exists", true))
                .append("statusHistory.changedAt",
                        new org.bson.Document("$gte", startInstant).append("$lt", endInstant))
                .append("$and", java.util.List.of(
                        new org.bson.Document("$or", java.util.List.of(
                                new org.bson.Document("status", "DELIVERED"),
                                new org.bson.Document("paymentStatus", "PAID"),
                                new org.bson.Document("paymentStatus", "SUCCESS"))),
                        new org.bson.Document("paymentStatus",
                                new org.bson.Document("$ne", "REFUNDED"))
                )));
        var pipeline = java.util.List.of(
                match,
                new org.bson.Document("$unwind", "$statusHistory"),
                new org.bson.Document("$match", new org.bson.Document(
                        "$expr", new org.bson.Document(
                                "$in", java.util.List.of("$statusHistory.status",
                                        java.util.List.of("DELIVERED", "PAID", "SUCCESS"))))),
                new org.bson.Document("$sort",
                        new org.bson.Document("statusHistory.changedAt", -1)),
                new org.bson.Document("$group",
                        new org.bson.Document("_id", "$_id")
                                .append("revenueAt",
                                        new org.bson.Document("$first", "$statusHistory.changedAt"))
                                .append("totalAmount",
                                        new org.bson.Document("$first", "$totalAmount"))),
                new org.bson.Document("$match",
                        new org.bson.Document("revenueAt",
                                new org.bson.Document("$gte", startInstant).append("$lt", endInstant))),
                new org.bson.Document("$group",
                        new org.bson.Document("_id",
                                new org.bson.Document("$dateToString",
                                        new org.bson.Document("format", "%Y-%m-%d")
                                                .append("date", "$revenueAt")
                                                .append("timezone", "Asia/Ho_Chi_Minh")))
                                .append("revenue",
                                        new org.bson.Document("$sum",
                                                new org.bson.Document("$toDecimal", "$totalAmount"))))
        );

        List<Document> rows = new ArrayList<>();
        mongoTemplate.getCollection("orders").aggregate(pipeline).into(rows);
        return ResponseEntity.ok(ApiResponse.success(rows));
    }

    // ============================================================
    // DEBUG: Verify top-products report correctness.
    //
    // Returns THREE lists side-by-side so an admin can compare and see
    // whether the chart is lying. Remove after fix is verified.
    //
    //   1. report          ← same data the dashboard chart uses
    //   2. groundTruthA    ← use items.productName (snapshot at order time)
    //   3. groundTruthB    ← use products.name (current catalog name)
    //   4. orphans         ← productIds in orders.items that no longer exist
    //                        in the products collection (deleted products)
    // ============================================================
    @GetMapping("/_debug/top-products-verify")
    public ResponseEntity<ApiResponse<org.bson.Document>> debugTopProductsVerify(
            @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));

        // === Diagnostic: every distinct paymentStatus on the orders collection ===
        // This is what we need to see why a newly-placed order isn't showing up
        // in the chart — if its paymentStatus is not exactly "PAID" the report
        // filter excludes it.
        List<Document> paymentStatusHistogram = new ArrayList<>();
        mongoTemplate.getCollection("orders").aggregate(java.util.List.of(
                new org.bson.Document("$group",
                        new org.bson.Document("_id",
                                new org.bson.Document("paymentStatus", "$paymentStatus")
                                        .append("status", "$status"))
                                .append("count", new org.bson.Document("$sum", 1))))
                .into(paymentStatusHistogram);

        // All PAID orders (or whatever paymentStatus the production report uses),
        // so the caller can see whether newly-placed orders landed in the expected bucket.
        List<Document> allOrdersLite = new ArrayList<>();
        mongoTemplate.getCollection("orders")
                .find(new org.bson.Document())
                .projection(new org.bson.Document()
                        .append("createdAt", 1)
                        .append("paymentStatus", 1)
                        .append("status", 1)
                        .append("items.productId", 1)
                        .append("items.quantity", 1)
                        .append("items.productName", 1))
                .sort(new org.bson.Document("createdAt", -1))
                .limit(50)
                .into(allOrdersLite);

        // === Variant 1: the production report (current chart source) ===
        List<TopProductResponse> report = reportingService.getTopProducts(safeLimit);

        // === Variant 2: ground truth from items.productName (snapshot) ===
        //   This is the name frozen at order-placement time. If a product was
        //   renamed later, this still shows the historical name the customer
        //   saw when they bought it.
        org.bson.Document matchPaid = new org.bson.Document("$match",
                new org.bson.Document("paymentStatus", "PAID"));
        org.bson.Document unwind = new org.bson.Document("$unwind", "$items");
        org.bson.Document addFields = new org.bson.Document("$addFields",
                new org.bson.Document("productIdStr",
                        new org.bson.Document("$toString", "$items.productId")));
        org.bson.Document groupSnapshot = new org.bson.Document("$group",
                new org.bson.Document("_id", "$productIdStr")
                        .append("productName",
                                new org.bson.Document("$first", "$items.productName"))
                        .append("totalQuantitySold",
                                new org.bson.Document("$sum", "$items.quantity")));
        org.bson.Document sortDesc = new org.bson.Document("$sort",
                new org.bson.Document("totalQuantitySold", -1));
        org.bson.Document limitStage = new org.bson.Document("$limit", safeLimit);

        List<Document> groundTruthA = new ArrayList<>();
        mongoTemplate.getCollection("orders").aggregate(java.util.List.of(
                matchPaid, unwind, addFields, groupSnapshot, sortDesc, limitStage
        )).into(groundTruthA);

        // === Variant 3: ground truth from products.name (current catalog) ===
        org.bson.Document lookup = new org.bson.Document("$lookup",
                new org.bson.Document("from", "products")
                        .append("let", new org.bson.Document("pid", "$_id"))
                        .append("pipeline", java.util.List.of(
                                new org.bson.Document("$match",
                                        new org.bson.Document("$expr",
                                                new org.bson.Document("$eq", java.util.List.of(
                                                        "$_id",
                                                        new org.bson.Document("$toObjectId", "$$pid")))))))
                        .append("as", "product"));
        org.bson.Document unwindProduct = new org.bson.Document("$unwind",
                new org.bson.Document("path", "$product")
                        .append("preserveNullAndEmptyArrays", true));
        org.bson.Document project = new org.bson.Document("$project",
                new org.bson.Document("productId", "$_id")
                        .append("productName",
                                new org.bson.Document("$ifNull", java.util.List.of(
                                        "$product.name",
                                        new org.bson.Document("$ifNull", java.util.List.of(
                                                "$_id", "Sản phẩm đã xoá")))))
                        .append("totalQuantitySold", 1)
                        .append("_id", 0));

        List<Document> groundTruthB = new ArrayList<>();
        mongoTemplate.getCollection("orders").aggregate(java.util.List.of(
                matchPaid, unwind, addFields, groupSnapshot, sortDesc, limitStage,
                lookup, unwindProduct, project
        )).into(groundTruthB);

        // === Variant 4: orphans — productIds in paid orders with no matching product ===
        List<String> paidProductIds = new ArrayList<>();
        for (Document d : mongoTemplate.getCollection("orders").aggregate(java.util.List.of(
                matchPaid, unwind, addFields,
                new org.bson.Document("$group",
                        new org.bson.Document("_id", "$productIdStr"))
        )).into(new ArrayList<>())) {
            paidProductIds.add(d.getString("_id"));
        }
        List<String> orphans = new ArrayList<>();
        if (!paidProductIds.isEmpty()) {
            List<org.bson.types.ObjectId> oidList = new ArrayList<>();
            for (String s : paidProductIds) {
                if (org.bson.types.ObjectId.isValid(s)) {
                    oidList.add(new org.bson.types.ObjectId(s));
                }
            }
            List<Document> found = new ArrayList<>();
            if (!oidList.isEmpty()) {
                mongoTemplate.getCollection("products")
                        .find(new org.bson.Document("_id",
                                new org.bson.Document("$in", oidList)))
                        .projection(new org.bson.Document("_id", 1))
                        .into(found);
            }
            java.util.Set<String> foundIds = new java.util.HashSet<>();
            for (Document d : found) foundIds.add(d.getObjectId("_id").toHexString());
            for (String s : paidProductIds) {
                if (!foundIds.contains(s)) orphans.add(s);
            }
        }

        // === Build a side-by-side comparison ===
        // For each (productId) present in any list, capture the qty and the
        // three different name sources so the admin can see exactly which
        // one is wrong (if any).
        java.util.Map<String, Document> comparison = new java.util.LinkedHashMap<>();
        java.util.function.BiConsumer<TopProductResponse, String> put = (row, source) -> {
            String pid = row.getProductId();
            if (pid == null) return;
            Document e = comparison.computeIfAbsent(pid, k -> new Document("productId", pid));
            e.append("qty", row.getTotalQuantitySold());
            e.append(source, row.getProductName());
        };
        for (TopProductResponse r : report) put.accept(r, "reportName");
        for (Document d : groundTruthA) {
            String pid = d.getString("_id");
            if (pid == null) continue;
            Document e = comparison.computeIfAbsent(pid, k -> new Document("productId", pid));
            e.append("snapshotName", d.getString("productName"));
            if (e.get("qty") == null) e.append("qty", d.get("totalQuantitySold"));
        }
        for (Document d : groundTruthB) {
            String pid = d.getString("productId");
            if (pid == null) continue;
            Document e = comparison.computeIfAbsent(pid, k -> new Document("productId", pid));
            e.append("catalogName", d.getString("productName"));
            if (e.get("qty") == null) e.append("qty", d.get("totalQuantitySold"));
        }

        // Add qty from the truth queries for any productId only present in those.
        for (Document d : groundTruthA) {
            String pid = d.getString("_id");
            if (pid == null) continue;
            Document e = comparison.computeIfAbsent(pid, k -> new Document("productId", pid));
            if (e.get("qty") == null) e.append("qty", d.get("totalQuantitySold"));
        }

        org.bson.Document result = new org.bson.Document()
                .append("limit", safeLimit)
                .append("reportCount", report.size())
                .append("groundTruthACount", groundTruthA.size())
                .append("groundTruthBCount", groundTruthB.size())
                .append("orphansCount", orphans.size())
                .append("orphans", orphans)
                .append("paymentStatusHistogram", paymentStatusHistogram)
                .append("recentOrders", allOrdersLite)
                .append("comparison", new ArrayList<>(comparison.values()))
                .append("note",
                        "If reportName == catalogName for every row, the chart is correct. "
                        + "If they differ, the report is fetching a stale/wrong name. "
                        + "snapshotName is the name captured at order-placement time "
                        + "(historical); catalogName is the current product name. "
                        + "orphans lists productIds that no longer exist in the catalog. "
                        + "paymentStatusHistogram shows every (paymentStatus, status) "
                        + "combination currently in the orders collection — use it to see "
                        + "why an order isn't counted by the report. recentOrders shows the "
                        + "50 most-recently-placed orders with their payment status, so you "
                        + "can confirm whether your test checkout landed in the expected bucket.");

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
