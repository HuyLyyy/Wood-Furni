// WOODFURNI - MongoDB Index Scripts for Carts Collection
// Run this script with: mongosh < database/indexes/carts.indexes.js
// Or import via MongoDB Compass / MongoDB Shell

use('woodfurni');

// ============================================================
// COLLECTION: carts
// Shopping cart collection - 1 active cart per user
// ============================================================

// Drop existing indexes (optional)
db.carts.dropIndexes();

// --- 1. Unique index on userId (1 cart per user) ---
db.carts.createIndex(
    { "userId": 1 },
    { unique: true, name: "userId_unique" }
);

// --- 2. Index for updatedAt (recent carts query) ---
db.carts.createIndex(
    { "updatedAt": -1 },
    { name: "updatedAt_desc" }
);

// ============================================================
// Verification
// ============================================================
print("=== Carts Collection Indexes ===");
db.carts.getIndexes().forEach(function(index) {
    print("Index: " + index.name);
    printjson(index.key);
    print("");
});
