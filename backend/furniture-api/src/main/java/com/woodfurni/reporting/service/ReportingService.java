package com.woodfurni.reporting.service;

import com.woodfurni.catalog.category.model.CategoryEnvironment;
import com.woodfurni.common.ApiResponse;
import com.woodfurni.reporting.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reporting / Dashboard service.
 *
 * IMPORTANT:
 * - All metrics are computed via MongoDB Aggregation Framework (MongoTemplate.aggregate)
 * - Data is NEVER loaded into Java for computation
 * - Each report is one aggregation method with inline comments explaining every $stage
 *
 * Collections used:
 * - orders
 * - users
 * - inventories
 * - products
 * - categories
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingService {

    private final MongoTemplate mongoTemplate;

    // Collections
    private static final String COL_ORDERS = "orders";
    private static final String COL_USERS = "users";
    private static final String COL_INVENTORIES = "inventories";
    private static final String COL_PRODUCTS = "products";
    private static final String COL_CATEGORIES = "categories";

    // ============================================================
    // REPORT 1: Dashboard summary
    // ============================================================
    /**
     * Dashboard summary metrics.
     * Combines four independent aggregations — all executed on the DB side.
     */
    public DashboardSummaryResponse getDashboardSummary() {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal revenueToday = aggregateRevenueToday(startOfDay);
        Long ordersToday = aggregateOrdersToday(startOfDay);
        Long newCustomersToday = aggregateNewCustomersToday(startOfDay);
        Long lowStockCount = aggregateLowStockCount();

        return DashboardSummaryResponse.builder()
                .revenueToday(revenueToday)
                .ordersToday(ordersToday)
                .newCustomersToday(newCustomersToday)
                .lowStockCount(lowStockCount)
                .build();
    }

    /**
     * Aggregation: sum(totalAmount) of orders with paymentStatus=PAID created today.
     *
     * Pipeline:
     *   $match  → filter by date range AND paymentStatus=PAID
     *   $group  → sum totalAmount into revenue
     */
    private BigDecimal aggregateRevenueToday(Instant startOfDay) {
        // Bucketed by event time: revenue counted on the day the order flipped
        // to DELIVERED / PAID / SUCCESS — not the day it was created. This
        // matches the daily chart logic and is what users expect from
        // "Doanh thu hôm nay" (revenue realized today).
        Instant endOfDay = startOfDay.plus(1, ChronoUnit.DAYS);

        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> stages = new ArrayList<>();
        stages.add(Aggregation.match(
                new org.springframework.data.mongodb.core.query.Criteria()
                        .and("statusHistory.0").exists(true)
                        .and("statusHistory.changedAt").gte(startOfDay).lt(endOfDay)
                        .andOperator(
                                new org.springframework.data.mongodb.core.query.Criteria()
                                        .orOperator(
                                                new org.springframework.data.mongodb.core.query.Criteria().and("status").is("DELIVERED"),
                                                new org.springframework.data.mongodb.core.query.Criteria().and("paymentStatus").is("PAID"),
                                                new org.springframework.data.mongodb.core.query.Criteria().and("paymentStatus").is("SUCCESS")
                                        ),
                                new org.springframework.data.mongodb.core.query.Criteria()
                                        .and("paymentStatus").ne("REFUNDED")
                        )));
        stages.add(ctx -> new Document("$unwind", "$statusHistory"));
        stages.add(ctx -> new Document("$match",
                new Document("$expr",
                        new Document("$in", List.of("$statusHistory.status",
                                List.of("DELIVERED", "PAID", "SUCCESS"))))));
        stages.add(ctx -> new Document("$sort", new Document("statusHistory.changedAt", -1)));
        stages.add(ctx -> new Document("$group",
                new Document("_id", "$_id")
                        .append("revenueAt",
                                new Document("$first", "$statusHistory.changedAt"))
                        .append("totalAmount", new Document("$first", "$totalAmount"))));
        stages.add(ctx -> new Document("$match",
                new Document("revenueAt",
                        new Document("$gte", startOfDay)
                                .append("$lt", endOfDay))));
        stages.add(ctx -> new Document("$group",
                new Document("_id", null)
                        .append("revenue",
                                new Document("$sum", "$totalAmount"))));

        Aggregation pipeline = Aggregation.newAggregation(stages);

        AggregationResults<Document> results = mongoTemplate.aggregate(
                pipeline, COL_ORDERS, Document.class);

        Document doc = results.getUniqueMappedResult();
        if (doc == null || doc.get("revenue") == null) {
            return BigDecimal.ZERO;
        }
        return toBigDecimal(doc.get("revenue"));
    }

    /**
     * Builds the $match Criteria for revenue calculations.
     *
     * Revenue counts orders where ALL of these hold:
     *   - paymentStatus != REFUNDED (refunds never count as revenue)
     *   - AND at least ONE of:
     *       (a) status = DELIVERED          — COD confirmed delivered
     *       (b) paymentStatus = PAID        — legacy sandbox paid
     *       (c) paymentStatus = SUCCESS     — gateway-aligned paid on Order
     *
     * @param start  lower bound Instant (inclusive), or null to skip
     * @param end    upper bound Instant (exclusive), or null to skip
     * @param year   year for date-bucket grouping, or null
     * @param month  month (1-12) for date-bucket grouping, or null
     */
    private org.springframework.data.mongodb.core.query.Criteria revenueCriteria(
            Instant start, Instant end, Integer year, Integer month) {

        var b = new org.springframework.data.mongodb.core.query.Criteria();

        // Exclude REFUNDED orders from revenue (already excluded by default, but
        // explicit is clearer and guards against future schema changes).
        var statusCondition = new org.springframework.data.mongodb.core.query.Criteria()
                .and("paymentStatus").ne("REFUNDED");

        // Include DELIVERED (COD) OR PAID/SUCCESS (prepaid).
        // Order.status is a String enum (DELIVERED, CONFIRMED, ...).
        // PaymentStatus values are PAID / SUCCESS (gateway-aligned) stored on Order.paymentStatus.
        var revenueCondition = new org.springframework.data.mongodb.core.query.Criteria().orOperator(
                new org.springframework.data.mongodb.core.query.Criteria().and("status").is("DELIVERED"),
                new org.springframework.data.mongodb.core.query.Criteria().and("paymentStatus").is("PAID"),
                new org.springframework.data.mongodb.core.query.Criteria().and("paymentStatus").is("SUCCESS")
        );

        if (start != null) b = b.and("createdAt").gte(start);
        if (end != null)   b = b.and("createdAt").lt(end);

        // For year/month-bucketed reports, also match the year/month on createdAt.
        // We do this via $expr to avoid needing a separate index.
        if (year != null && month != null) {
            var dateCondition = new org.springframework.data.mongodb.core.query.Criteria()
                    .andOperator(
                            new org.springframework.data.mongodb.core.query.Criteria()
                                    .and("createdAt").gte(java.time.LocalDate.of(year, month, 1)
                                            .atStartOfDay(java.time.ZoneOffset.UTC).toInstant()),
                            new org.springframework.data.mongodb.core.query.Criteria()
                                    .and("createdAt").lt(java.time.LocalDate.of(year, month, 1)
                                            .plusMonths(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant())
                    );
            return new org.springframework.data.mongodb.core.query.Criteria().andOperator(
                    statusCondition, revenueCondition, dateCondition);
        }

        return new org.springframework.data.mongodb.core.query.Criteria().andOperator(
                statusCondition, revenueCondition);
    }

    /**
     * Overload for callers that only need start/end bounds (no year/month bucket).
     */
    private org.springframework.data.mongodb.core.query.Criteria revenueCriteria(
            Instant start, Instant end) {
        return revenueCriteria(start, end, null, null);
    }

    /**
     * Overload for callers that need start only (end = null, no bucket).
     */
    private org.springframework.data.mongodb.core.query.Criteria revenueCriteria(
            Instant start) {
        return revenueCriteria(start, null, null, null);
    }

    /**
     * Aggregation: count orders created today (regardless of payment status).
     *
     * Pipeline:
     *   $match → filter by createdAt >= startOfDay
     *   $count → total
     */
    private Long aggregateOrdersToday(Instant startOfDay) {
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> stages = new ArrayList<>();
        stages.add(Aggregation.match(
                new org.springframework.data.mongodb.core.query.Criteria()
                        .and("createdAt").gte(startOfDay)));
        stages.add(ctx -> new Document("$count", "count"));

        Aggregation pipeline = Aggregation.newAggregation(stages);

        AggregationResults<Document> results = mongoTemplate.aggregate(
                pipeline, COL_ORDERS, Document.class);

        Document doc = results.getUniqueMappedResult();
        return doc == null ? 0L : ((Number) doc.get("count")).longValue();
    }

    /**
     * Aggregation: count new CUSTOMER-role users created today.
     *
     * Pipeline:
     *   $match → role=CUSTOMER AND createdAt >= startOfDay
     *   $count → total
     */
    private Long aggregateNewCustomersToday(Instant startOfDay) {
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> stages = new ArrayList<>();
        stages.add(Aggregation.match(
                new org.springframework.data.mongodb.core.query.Criteria()
                        .and("role").is("CUSTOMER")
                        .and("createdAt").gte(startOfDay)));
        stages.add(ctx -> new Document("$count", "count"));

        Aggregation pipeline = Aggregation.newAggregation(stages);

        AggregationResults<Document> results = mongoTemplate.aggregate(
                pipeline, COL_USERS, Document.class);

        Document doc = results.getUniqueMappedResult();
        return doc == null ? 0L : ((Number) doc.get("count")).longValue();
    }

    /**
     * Aggregation: count inventory items where quantityOnHand <= lowStockThreshold.
     *
     * Pipeline:
     *   $match → quantityOnHand <= lowStockThreshold
     *   $count → total
     *
     * Note: uses $expr to compare two fields of the same document.
     */
    private Long aggregateLowStockCount() {
        // Field-to-field comparison via $expr: $quantityOnHand <= $lowStockThreshold.
        // Build as raw Document to avoid Spring Data MongoDB entity-mapping NPE in $expr context.
        Aggregation pipeline = Aggregation.newAggregation(
                Aggregation.stage(new Document("$match",
                        new Document("$expr",
                                new Document("$lte",
                                        List.of("$quantityOnHand", "$lowStockThreshold"))))),
                Aggregation.stage(new Document("$count", "count"))
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(
                pipeline, COL_INVENTORIES, Document.class);

        Document doc = results.getUniqueMappedResult();
        return doc == null ? 0L : ((Number) doc.get("count")).longValue();
    }

    // ============================================================
    // REPORT 2: Revenue by month (last 12 months)
    // ============================================================
    /**
     * Returns monthly revenue for the last 12 months, including months with zero revenue.
     *
     * Pipeline:
     *   $match   → paymentStatus=PAID AND createdAt >= 12 months ago
     *   $group   → group by yyyy-MM, sum totalAmount
     *   $project → reshape
     *   $sort    → ascending by month
     *
     * Then fills in missing months with zero revenue on the Java side.
     */
    public List<MonthlyRevenueResponse> getMonthlyRevenue() {
        Instant twelveMonthsAgo = LocalDate.now(ZoneOffset.UTC)
                .minusMonths(11).withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        // Pipeline (revenue bucketed by EVENT TIME, not createdAt):
        //   $match    → revenue-eligible orders touched in last 12 months
        //   $unwind   → explode statusHistory
        //   $match    → keep only the entry where status flipped to
        //               DELIVERED / PAID / SUCCESS (the moment revenue was recognized)
        //   $sort     → most recent event first
        //   $group    → one revenueAt per order (the latest recognition event)
        //   $group    → bucket by yyyy-MM of revenueAt, sum totalAmount
        //   $project  → reshape
        //   $sort     → by month ascending
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> stages = new ArrayList<>();
        // Bound the recognition event into the same 12-month window as before so
        // older orders that get paid later still show up in the chart.
        stages.add(Aggregation.match(
                new org.springframework.data.mongodb.core.query.Criteria()
                        .and("statusHistory.0").exists(true)
                        .and("statusHistory.changedAt").gte(twelveMonthsAgo)
                        .andOperator(
                                new org.springframework.data.mongodb.core.query.Criteria()
                                        .orOperator(
                                                new org.springframework.data.mongodb.core.query.Criteria().and("status").is("DELIVERED"),
                                                new org.springframework.data.mongodb.core.query.Criteria().and("paymentStatus").is("PAID"),
                                                new org.springframework.data.mongodb.core.query.Criteria().and("paymentStatus").is("SUCCESS")
                                        ),
                                new org.springframework.data.mongodb.core.query.Criteria()
                                        .and("paymentStatus").ne("REFUNDED")
                        )));
        stages.add(ctx -> new Document("$unwind", "$statusHistory"));
        stages.add(ctx -> new Document("$match",
                new Document("$expr",
                        new Document("$in", List.of("$statusHistory.status",
                                List.of("DELIVERED", "PAID", "SUCCESS"))))));
        stages.add(ctx -> new Document("$sort", new Document("statusHistory.changedAt", -1)));
        stages.add(ctx -> new Document("$group",
                new Document("_id", "$_id")
                        .append("revenueAt",
                                new Document("$first", "$statusHistory.changedAt"))
                        .append("totalAmount", new Document("$first", "$totalAmount"))));
        stages.add(ctx -> new Document("$group",
                new Document("_id",
                        new Document("$dateToString",
                                new Document("format", "%Y-%m")
                                        .append("date", "$revenueAt")
                                        .append("timezone", "UTC")))
                        .append("revenue",
                                new Document("$sum", "$totalAmount"))));
        stages.add(ctx -> new Document("$project",
                new Document("month", "$_id")
                        .append("revenue", 1)
                        .append("_id", 0)));
        stages.add(ctx -> new Document("$sort", new Document("month", 1)));

        Aggregation pipeline = Aggregation.newAggregation(stages);

        AggregationResults<Document> results = mongoTemplate.aggregate(
                pipeline, COL_ORDERS, Document.class);

        Map<String, BigDecimal> revenueByMonth = new java.util.HashMap<>();
        for (Document doc : results) {
            revenueByMonth.put(doc.getString("month"), toBigDecimal(doc.get("revenue")));
        }

        // Fill in 12 months, including those with zero revenue
        List<MonthlyRevenueResponse> response = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        LocalDate startMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);

        for (int i = 11; i >= 0; i--) {
            LocalDate month = startMonth.minusMonths(i);
            String key = month.format(formatter);
            response.add(MonthlyRevenueResponse.builder()
                    .month(key)
                    .revenue(revenueByMonth.getOrDefault(key, BigDecimal.ZERO))
                    .build());
        }

        return response;
    }

    /**
     * Daily revenue for a single calendar month (1..daysInMonth rows).
     * Returns yyyy-MM-dd keys; zero-filled for days with no orders.
     *
     * Buckets revenue by EVENT TIME (statusHistory[].changedAt of the entry
     * where status flipped to DELIVERED / PAID / SUCCESS) — not by createdAt —
     * so a COD order created yesterday but delivered today shows up on today's bar.
     *
     * Pipeline:
     *   $match    → orders with at least one revenue recognition event in [start, endOfMonth]
     *   $unwind   → explode statusHistory
     *   $match    → keep entries where status flipped to DELIVERED/PAID/SUCCESS
     *   $sort     → most recent first
     *   $group    → one revenueAt per order (latest recognition event)
     *   $match    → restrict to the requested month
     *   $group    → bucket by yyyy-MM-dd of revenueAt, sum totalAmount
     *   $project  → rename _id → date
     *   $sort     → by date ascending
     */
    public List<DailyRevenueResponse> getDailyRevenue(int year, int month) {
        // Defensive: clamp month/year to sane ranges so a bad input doesn't
        // crash the aggregation with a date-arithmetic error.
        if (year < 2000 || year > 3000) year = LocalDate.now(ZoneOffset.UTC).getYear();
        if (month < 1) month = 1;
        if (month > 12) month = 12;

        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate nextMonthFirstDay = firstDay.plusMonths(1);
        Instant startInstant = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = nextMonthFirstDay.atStartOfDay(ZoneOffset.UTC).toInstant();

        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> stages = new ArrayList<>();
        stages.add(Aggregation.match(
                new org.springframework.data.mongodb.core.query.Criteria()
                        .and("statusHistory.0").exists(true)
                        .and("statusHistory.changedAt").gte(startInstant).lt(endInstant)
                        .andOperator(
                                new org.springframework.data.mongodb.core.query.Criteria()
                                        .orOperator(
                                                new org.springframework.data.mongodb.core.query.Criteria().and("status").is("DELIVERED"),
                                                new org.springframework.data.mongodb.core.query.Criteria().and("paymentStatus").is("PAID"),
                                                new org.springframework.data.mongodb.core.query.Criteria().and("paymentStatus").is("SUCCESS")
                                        ),
                                new org.springframework.data.mongodb.core.query.Criteria()
                                        .and("paymentStatus").ne("REFUNDED")
                        )));
        stages.add(ctx -> new Document("$unwind", "$statusHistory"));
        stages.add(ctx -> new Document("$match",
                new Document("$expr",
                        new Document("$in", List.of("$statusHistory.status",
                                List.of("DELIVERED", "PAID", "SUCCESS"))))));
        stages.add(ctx -> new Document("$sort", new Document("statusHistory.changedAt", -1)));
        stages.add(ctx -> new Document("$group",
                new Document("_id", "$_id")
                        .append("revenueAt",
                                new Document("$first", "$statusHistory.changedAt"))
                        .append("totalAmount", new Document("$first", "$totalAmount"))));
        stages.add(ctx -> new Document("$match",
                new Document("revenueAt",
                        new Document("$gte", startInstant)
                                .append("$lt", endInstant))));
        stages.add(ctx -> new Document("$group",
                new Document("_id",
                        new Document("$dateToString",
                                new Document("format", "%Y-%m-%d")
                                        .append("date", "$revenueAt")
                                        .append("timezone", "UTC")))
                        .append("revenue",
                                new Document("$sum", "$totalAmount"))));
        stages.add(ctx -> new Document("$project",
                new Document("date", "$_id")
                        .append("revenue", 1)
                        .append("_id", 0)));
        stages.add(ctx -> new Document("$sort", new Document("date", 1)));

        Aggregation pipeline = Aggregation.newAggregation(stages);

        AggregationResults<Document> results = mongoTemplate.aggregate(
                pipeline, COL_ORDERS, Document.class);

        Map<String, BigDecimal> revenueByDate = new java.util.HashMap<>();
        for (Document doc : results) {
            revenueByDate.put(doc.getString("date"), toBigDecimal(doc.get("revenue")));
        }

        // Fill all days in the month, zero for days with no orders.
        List<DailyRevenueResponse> response = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        int daysInMonth = firstDay.lengthOfMonth();
        for (int d = 1; d <= daysInMonth; d++) {
            String key = LocalDate.of(year, month, d).format(formatter);
            response.add(DailyRevenueResponse.builder()
                    .date(key)
                    .revenue(revenueByDate.getOrDefault(key, BigDecimal.ZERO))
                    .build());
        }

        return response;
    }

    // ============================================================
    // REPORT 3: Orders by status
    // ============================================================
    /**
     * Groups all orders by status, returns count per status.
     *
     * Pipeline:
     *   $group → group by status, count
     *   $project → rename
     *   $sort → by status asc
     */
    public List<OrdersByStatusResponse> getOrdersByStatus() {
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> stages = new ArrayList<>();
        stages.add(ctx -> new Document("$group",
                new Document("_id", "$status")
                        .append("count", new Document("$sum", 1))));
        stages.add(ctx -> new Document("$project",
                new Document("status", "$_id")
                        .append("count", 1)
                        .append("_id", 0)));
        stages.add(ctx -> new Document("$sort", new Document("status", 1)));

        Aggregation pipeline = Aggregation.newAggregation(stages);

        AggregationResults<Document> results = mongoTemplate.aggregate(
                pipeline, COL_ORDERS, Document.class);

        List<OrdersByStatusResponse> list = new ArrayList<>();
        for (Document doc : results) {
            list.add(OrdersByStatusResponse.builder()
                    .status(doc.getString("status"))
                    .count(((Number) doc.get("count")).longValue())
                    .build());
        }
        return list;
    }

    // ============================================================
    // REPORT 4: Top selling products
    // ============================================================
    /**
     * Top N selling products by total quantity sold.
     *
     * Pipeline:
     *   $match  → only PAID orders (revenue matters)
     *   $unwind → flatten Order.items array
     *   $group  → group by productId, sum quantity
     *   $sort   → desc by totalQuantitySold
     *   $limit  → top N
     *   $lookup → join products collection to fetch name
     *   $project → reshape
     */
    public List<TopProductResponse> getTopProducts(int limit) {
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> stages = new ArrayList<>();
        stages.add(Aggregation.match(
                new org.springframework.data.mongodb.core.query.Criteria()
                        .and("paymentStatus").is("PAID")));
        stages.add(ctx -> new Document("$unwind", "$items"));
        stages.add(ctx -> new Document("$group",
                new Document("_id", "$items.productId")
                        .append("totalQuantitySold",
                                new Document("$sum", "$items.quantity"))));
        stages.add(ctx -> new Document("$sort",
                new Document("totalQuantitySold", -1)));
        stages.add(ctx -> new Document("$limit", limit));
        stages.add(ctx -> new Document("$lookup",
                new Document("from", COL_PRODUCTS)
                        .append("localField", "_id")
                        .append("foreignField", "_id")
                        .append("as", "product")));
        stages.add(ctx -> new Document("$unwind",
                new Document("path", "$product")
                        .append("preserveNullAndEmptyArrays", true)));
        stages.add(ctx -> new Document("$project",
                new Document("productId", "$_id")
                        .append("productName", "$product.name")
                        .append("totalQuantitySold", 1)
                        .append("_id", 0)));

        Aggregation pipeline = Aggregation.newAggregation(stages);

        AggregationResults<Document> results = mongoTemplate.aggregate(
                pipeline, COL_ORDERS, Document.class);

        List<TopProductResponse> list = new ArrayList<>();
        for (Document doc : results) {
            Object pidObj = doc.get("productId");
            String productId = pidObj == null ? null : pidObj.toString();
            String productName = doc.getString("productName");
            Number totalSold = (Number) doc.get("totalQuantitySold");
            list.add(TopProductResponse.builder()
                    .productId(productId)
                    .productName(productName)
                    .totalQuantitySold(totalSold == null ? 0L : totalSold.longValue())
                    .build());
        }
        return list;
    }

    // ============================================================
    // REPORT 5: Category breakdown (revenue by Indoor/Outdoor + category)
    // ============================================================
    /**
     * Revenue breakdown by environment (INDOOR / OUTDOOR) and category.
     *
     * Pipeline:
     *   $match  → paymentStatus=PAID
     *   $unwind → flatten Order.items
     *   $lookup → join products (to get categoryId + environment)
     *   $unwind → product
     *   $lookup → join categories (to get category name)
     *   $unwind → category
     *   $group  → by environment + categoryId, sum revenue (item subtotal)
     *   $project → reshape
     *   $sort   → by revenue desc
     */
    public List<CategoryBreakdownResponse> getCategoryBreakdown() {
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> stages = new ArrayList<>();
        stages.add(ctx -> new Document("$match",
                new Document("paymentStatus", "PAID")));
        stages.add(ctx -> new Document("$unwind", "$items"));
        stages.add(ctx -> new Document("$lookup",
                new Document("from", COL_PRODUCTS)
                        .append("localField", "items.productId")
                        .append("foreignField", "_id")
                        .append("as", "product")));
        stages.add(ctx -> new Document("$unwind",
                new Document("path", "$product")
                        .append("preserveNullAndEmptyArrays", true)));
        stages.add(ctx -> new Document("$match",
                new Document("product",
                        new Document("$ne", java.util.Collections.singletonList(new java.util.HashMap<>())))));
        stages.add(ctx -> new Document("$lookup",
                new Document("from", COL_CATEGORIES)
                        .append("localField", "product.categoryId")
                        .append("foreignField", "_id")
                        .append("as", "category")));
        stages.add(ctx -> new Document("$unwind",
                new Document("path", "$category")
                        .append("preserveNullAndEmptyArrays", true)));
        stages.add(ctx -> new Document("$group",
                new Document("_id",
                        new Document("environment", "$product.environment")
                                .append("categoryId", "$product.categoryId")
                                .append("categoryName", "$category.name"))
                        .append("revenue",
                                new Document("$sum",
                                        new Document("$multiply",
                                                List.of("$items.unitPrice", "$items.quantity"))))
                        .append("orderCount",
                                new Document("$sum", 1))));
        stages.add(ctx -> new Document("$project",
                new Document("environment", "$_id.environment")
                        .append("categoryId", "$_id.categoryId")
                        .append("categoryName", "$_id.categoryName")
                        .append("revenue", 1)
                        .append("orderCount", 1)
                        .append("_id", 0)));
        stages.add(ctx -> new Document("$sort", new Document("revenue", -1)));

        Aggregation pipeline = Aggregation.newAggregation(stages);

        AggregationResults<Document> results = mongoTemplate.aggregate(
                pipeline, COL_ORDERS, Document.class);

        List<CategoryBreakdownResponse> list = new ArrayList<>();
        for (Document doc : results) {
            Object envObj = doc.get("environment");
            Object catIdObj = doc.get("categoryId");
            String environment = envObj == null ? null : envObj.toString();
            String categoryId = catIdObj == null ? null : catIdObj.toString();
            String categoryName = doc.getString("categoryName");
            Number orderCount = (Number) doc.get("orderCount");
            list.add(CategoryBreakdownResponse.builder()
                    .environment(environment)
                    .categoryId(categoryId)
                    .categoryName(categoryName)
                    .revenue(toBigDecimal(doc.get("revenue")))
                    .orderCount(orderCount == null ? 0L : orderCount.longValue())
                    .build());
        }
        return list;
    }

    // ============================================================
    // Helpers
    // ============================================================
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(value.toString());
    }
}
