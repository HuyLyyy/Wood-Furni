package com.woodfurni.reporting.controller;

import com.woodfurni.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug endpoints for dashboard diagnostics — no authentication required.
 *
 * These endpoints bypass @PreAuthorize at the class level of ReportingController
 * so they can be called with curl/Postman without a JWT.
 *
 * All endpoints are open publicly because they return raw internal data
 * (aggregation pipeline results) intended only for developers.
 *
 * TODO: remove these debug endpoints after dashboard is confirmed working.
 */
@RestController
@RequestMapping("/admin/dashboard/_debug")
@RequiredArgsConstructor
public class DebugController {

    private final MongoTemplate mongoTemplate;

    /**
     * Run the environment-revenue pipeline end-to-end and return the raw
     * aggregation rows. This lets us inspect exactly which orders contributed
     * and whether the pipeline succeeds.
     */
    @GetMapping("/revenue-by-environment-verify")
    public ResponseEntity<ApiResponse<List<Document>>> debugRevenueByEnvironment() {
        // Same simplified pipeline as ReportingService.getEnvironmentRevenue():
        // simple localField/foreignField $lookup (no $expr, no $toObjectId).
        //
        // Pipeline stages:
        //   $match       → exclude CANCELLED/RETURNED/PENDING_CANCEL, exclude REFUNDED
        //   $unwind      → flatten Order.items
        //   $addFields   → productId as string
        //   $lookup      → join products (MongoDB 4.4+ handles string↔ObjectId)
        //   $unwind      → explode product array
        //   $addFields   → normalise environment (BOTH/null → INDOOR, OUTDOOR → OUTDOOR)
        //   $match       → keep only INDOOR/OUTDOOR
        //   $group       → sum unitPrice * quantity per environment
        //   $project     → reshape to {environment, revenue, orderCount}

        Document orderMatch = new Document("$match",
                new Document("$and", List.of(
                        new Document("status",
                                new Document("$nin",
                                        List.of("CANCELLED", "RETURNED", "PENDING_CANCEL"))),
                        new Document("paymentStatus",
                                new Document("$ne", "REFUNDED"))
                )));

        Document unwindItems = new Document("$unwind", "$items");

        Document addProductIdStr = new Document("$addFields",
                new Document("productIdStr",
                        new Document("$toString", "$items.productId")));

        // Simple $lookup — MongoDB 4.4+ auto-converts string↔ObjectId on _id.
        Document lookupProduct = new Document("$lookup",
                new Document("from", "products")
                        .append("localField", "productIdStr")
                        .append("foreignField", "_id")
                        .append("as", "product"));

        // Drop rows where product no longer exists.
        Document unwindProduct = new Document("$unwind",
                new Document("path", "$product")
                        .append("preserveNullAndEmptyArrays", false));

        // Normalise environment: OUTDOOR stays OUTDOOR, everything else → INDOOR.
        Document normaliseEnv = new Document("$addFields",
                new Document("normalisedEnv",
                        new Document("$switch", new Document()
                                .append("branches", List.of(
                                        new Document("case",
                                                new Document("$eq", List.of(
                                                        "$product.environment", "OUTDOOR")))
                                                .append("then", "OUTDOOR")))
                                .append("default", "INDOOR"))));

        Document matchKnownEnv = new Document("$match",
                new Document("normalisedEnv",
                        new Document("$in", List.of("INDOOR", "OUTDOOR"))));

        // Sum unitPrice * quantity, guard nulls with $ifNull.
        Document groupByEnv = new Document("$group",
                new Document("_id", "$normalisedEnv")
                        .append("revenue",
                                new Document("$sum",
                                        new Document("$multiply", List.of(
                                                new Document("$ifNull", List.of("$items.unitPrice", 0)),
                                                new Document("$ifNull", List.of("$items.quantity", 0))))))
                        .append("orderCount",
                                new Document("$sum", 1)));

        Document project = new Document("$project",
                new Document("environment", "$_id")
                        .append("revenue", 1)
                        .append("orderCount", 1)
                        .append("_id", 0));

        List<Document> pipeline = List.of(
                orderMatch, unwindItems, addProductIdStr, lookupProduct,
                unwindProduct, normaliseEnv, matchKnownEnv, groupByEnv, project
        );

        List<Document> rows = new ArrayList<>();
        mongoTemplate.getCollection("orders").aggregate(pipeline).into(rows);
        return ResponseEntity.ok(ApiResponse.success(rows));
    }

    /**
     * Return a list of the most recent orders so we can inspect their
     * paymentStatus and status values. Use this to understand why
     * orders aren't being counted in the revenue pipeline.
     */
    @GetMapping("/orders-recent")
    public ResponseEntity<ApiResponse<List<Document>>> debugOrdersRecent(
            @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<Document> rows = new ArrayList<>();
        mongoTemplate.getCollection("orders")
                .find(new Document())
                .projection(new Document()
                        .append("_id", 1)
                        .append("orderNumber", 1)
                        .append("createdAt", 1)
                        .append("status", 1)
                        .append("paymentStatus", 1)
                        .append("items.productId", 1)
                        .append("items.productName", 1)
                        .append("items.quantity", 1)
                        .append("items.unitPrice", 1))
                .sort(new Document("createdAt", -1))
                .limit(safeLimit)
                .into(rows);
        return ResponseEntity.ok(ApiResponse.success(rows));
    }

    /**
     * Same as revenue-by-environment-verify, but uses $toObjectId in the
     * $lookup so we can compare which variant succeeds. If this returns
     * non-empty data but the original returns [] → MongoDB isn't
     * auto-converting string↔ObjectId in the localField/foreignField form.
     */
    @GetMapping("/revenue-by-environment-verify-toobjectid")
    public ResponseEntity<ApiResponse<List<Document>>> debugRevenueByEnvironmentToObjectId() {
        Document unwindItems = new Document("$unwind", "$items");

        Document addProductIdStr = new Document("$addFields",
                new Document("productIdStr",
                        new Document("$toString", "$items.productId")));

        // Use $expr with $toObjectId to force the type conversion
        Document lookupProduct = new Document("$lookup",
                new Document("from", "products")
                        .append("let", new Document("pid", "$productIdStr"))
                        .append("pipeline", List.of(
                                new Document("$match", new Document("$expr",
                                        new Document("$eq", List.of(
                                                "$_id",
                                                new Document("$toObjectId", "$$pid")))))))
                        .append("as", "product"));

        Document unwindProduct = new Document("$unwind",
                new Document("path", "$product")
                        .append("preserveNullAndEmptyArrays", false));

        Document normaliseEnv = new Document("$addFields",
                new Document("normalisedEnv",
                        new Document("$switch", new Document()
                                .append("branches", List.of(
                                        new Document("case",
                                                new Document("$eq", List.of(
                                                        "$product.environment", "OUTDOOR")))
                                                .append("then", "OUTDOOR")))
                                .append("default", "INDOOR"))));

        Document groupByEnv = new Document("$group",
                new Document("_id", "$normalisedEnv")
                        .append("revenue",
                                new Document("$sum",
                                        new Document("$multiply", List.of(
                                                new Document("$ifNull", List.of("$items.unitPrice", 0)),
                                                new Document("$ifNull", List.of("$items.quantity", 0))))))
                        .append("orderCount", new Document("$sum", 1)));

        Document project = new Document("$project",
                new Document("environment", "$_id")
                        .append("revenue", 1)
                        .append("orderCount", 1)
                        .append("_id", 0));

        List<Document> pipeline = List.of(
                unwindItems, addProductIdStr, lookupProduct,
                unwindProduct, normaliseEnv, groupByEnv, project
        );

        List<Document> rows = new ArrayList<>();
        try {
            mongoTemplate.getCollection("orders").aggregate(pipeline).into(rows);
        } catch (org.springframework.dao.DataAccessException ex) {
            List<Document> errorList = new ArrayList<>();
            Throwable cause = ex.getCause();
            errorList.add(new Document("error", cause != null ? cause.getMessage() : ex.getMessage()));
            return ResponseEntity.ok(ApiResponse.success(errorList));
        }
        return ResponseEntity.ok(ApiResponse.success(rows));
    }

    /**
     * Diagnostic: return all orders with their productId values cast to string,
     * plus the actual types so we can see if productId is already ObjectId or string.
     */
    @GetMapping("/revenue-lookup-debug")
    public ResponseEntity<ApiResponse<List<Document>>> debugRevenueLookup() {
        // Step 1: just $unwind + $addFields to see what productId looks like
        Document unwindItems = new Document("$unwind", "$items");

        Document addProductIdStr = new Document("$addFields",
                new Document("productIdStr",
                        new Document("$toString", "$items.productId")));

        Document addOriginalType = new Document("$addFields",
                new Document("productIdOriginal",
                        new Document("$type", "$items.productId")));

        Document project = new Document("$project",
                new Document("orderNumber", 1)
                        .append("productIdStr", "$productIdStr")
                        .append("productIdType", "$productIdOriginal")
                        .append("productIdRaw", "$items.productId"));

        Document limit = new Document("$limit", 5);

        List<Document> pipeline = List.of(unwindItems, addProductIdStr, addOriginalType, project, limit);

        List<Document> rows = new ArrayList<>();
        mongoTemplate.getCollection("orders").aggregate(pipeline).into(rows);

        // Step 2: try direct $lookup with localField/foreignField
        List<Document> lookupRows = new ArrayList<>();
        try {
            Document unwindItems2 = new Document("$unwind", "$items");
            Document addProductIdStr2 = new Document("$addFields",
                    new Document("productIdStr",
                            new Document("$toString", "$items.productId")));
            Document lookup = new Document("$lookup",
                    new Document("from", "products")
                            .append("localField", "productIdStr")
                            .append("foreignField", "_id")
                            .append("as", "product"));
            Document unwindProduct = new Document("$unwind",
                    new Document("path", "$product")
                            .append("preserveNullAndEmptyArrays", false));
            Document limit2 = new Document("$limit", 3);
            List<Document> pipeline2 = List.of(unwindItems2, addProductIdStr2, lookup, unwindProduct, limit2);
            mongoTemplate.getCollection("orders").aggregate(pipeline2).into(lookupRows);
        } catch (Exception ex) {
            lookupRows = List.of(new Document("lookupError", ex.getMessage()));
        }

        return ResponseEntity.ok(ApiResponse.success(List.of(
                new Document("step1_productIdTypes", rows),
                new Document("step2_lookupResult", lookupRows)
        )));
    }
}
