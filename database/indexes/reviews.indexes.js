// WOODFURNI - MongoDB Index Scripts for Reviews Collection
// Run this script with: mongosh < database/indexes/reviews.indexes.js

use('woodfurni');

db.reviews.dropIndexes();

// --- 1. Compound index: productId + createdAt (desc) ---
// Supports: GET /products/{productId}/reviews (paginated, newest first)
db.reviews.createIndex(
    { "productId": 1, "createdAt": -1 },
    { name: "productId_createdAt_idx" }
);

// --- 2. Unique compound index: userId + productId + orderId ---
// Prevents duplicate reviews: one review per user per product per order
db.reviews.createIndex(
    { "userId": 1, "productId": 1, "orderId": 1 },
    { unique: true, name: "user_product_order_unique_idx" }
);

// --- 3. Index for user review history ---
db.reviews.createIndex(
    { "userId": 1 },
    { name: "userId_idx" }
);

// --- 4. Index for status filtering (admin queries) ---
db.reviews.createIndex(
    { "status": 1 },
    { name: "status_idx" }
);

// --- 5. Index for order review check ---
db.reviews.createIndex(
    { "orderId": 1 },
    { name: "orderId_idx" }
);

print("=== Reviews Collection Indexes ===");
db.reviews.getIndexes().forEach(function(idx) {
    print("Index: " + idx.name);
    printjson(idx.key);
    print("");
});
