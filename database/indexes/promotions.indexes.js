// WOODFURNI - MongoDB Index Scripts for Promotions Collection
// Run this script with: mongosh < database/indexes/promotions.indexes.js
// Or import via MongoDB Compass / MongoDB Shell

use('woodfurni');

// ============================================================
// COLLECTION: promotions
// As defined in WOODFURNI spec Mục 3.3 - Promotion indexes
// ============================================================

// Drop existing indexes (optional)
db.promotions.dropIndexes();

// --- 1. Unique index on code ---
db.promotions.createIndex(
    { "code": 1 },
    { unique: true, name: "code_unique" }
);

// --- 2. Index for status + date range queries ---
// Supports: active promotions, expired check
db.promotions.createIndex(
    { "status": 1, "startDate": 1, "endDate": 1 },
    { name: "status_date_range_idx" }
);

// --- 3. Index for usage tracking ---
db.promotions.createIndex(
    { "usageLimit": 1, "usedCount": 1 },
    { name: "usage_tracking_idx" }
);

// ============================================================
// Verification
// ============================================================
print("=== Promotions Collection Indexes ===");
db.promotions.getIndexes().forEach(function(index) {
    print("Index: " + index.name);
    printjson(index.key);
    print("");
});
