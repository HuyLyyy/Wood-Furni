// WOODFURNI - MongoDB Index Scripts for Inventories Collection
// Run this script with: mongosh < database/indexes/inventories.indexes.js
// Or import via MongoDB Compass / MongoDB Shell

use('woodfurni');

// ============================================================
// COLLECTION: inventories
// As defined in WOODFURNI spec Mục 3.3 - Inventory indexes
// ============================================================

// Drop existing indexes (optional - only run if you want to recreate)
db.inventories.dropIndexes();

// --- 1. Unique index on productId (1 inventory per product) ---
db.inventories.createIndex(
    { "productId": 1 },
    { unique: true, name: "productId_unique" }
);

// --- 2. Index for low-stock queries ---
// Supports: GET /api/v1/inventory/low-stock
// query: quantityOnHand <= lowStockThreshold (default 5)
db.inventories.createIndex(
    { "quantityOnHand": 1 },
    { name: "quantityOnHand_idx" }
);

// --- 3. Index for reserved stock monitoring ---
db.inventories.createIndex(
    { "quantityReserved": 1 },
    { name: "quantityReserved_idx" }
);

// --- 4. Compound index for concurrent inventory operations ---
// Supports atomic reserve/release/commit operations
db.inventories.createIndex(
    { "productId": 1, "quantityOnHand": 1, "quantityReserved": 1 },
    { name: "productId_stock_status_idx" }
);

// ============================================================
// Verification: list all indexes created
// ============================================================
print("=== Inventories Collection Indexes ===");
db.inventories.getIndexes().forEach(function(index) {
    print("Index: " + index.name);
    printjson(index.key);
    print("");
});
