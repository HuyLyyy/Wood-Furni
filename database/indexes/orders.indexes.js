// WOODFURNI - MongoDB Index Scripts for Orders Collection
// Run this script with: mongosh < database/indexes/orders.indexes.js

use('woodfurni');

db.orders.dropIndexes();

// --- 1. Unique index on orderNumber ---
db.orders.createIndex(
    { "orderNumber": 1 },
    { unique: true, name: "orderNumber_unique" }
);

// --- 2. Index for customer order history ---
db.orders.createIndex(
    { "customerId": 1, "createdAt": -1 },
    { name: "customer_orders_idx" }
);

// --- 3. Index for status filtering ---
db.orders.createIndex(
    { "status": 1 },
    { name: "status_idx" }
);

// --- 4. Index for payment status ---
db.orders.createIndex(
    { "paymentStatus": 1 },
    { name: "paymentStatus_idx" }
);

// --- 5. Index for date range queries (revenue reports) ---
db.orders.createIndex(
    { "createdAt": -1 },
    { name: "createdAt_desc" }
);

print("=== Orders Collection Indexes ===");
db.orders.getIndexes().forEach(function(idx) {
    print("Index: " + idx.name);
    printjson(idx.key);
    print("");
});
