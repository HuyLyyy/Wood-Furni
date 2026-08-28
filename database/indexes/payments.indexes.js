// WOODFURNI - MongoDB Index Scripts for Payments Collection
// Run this script with: mongosh < database/indexes/payments.indexes.js

use('woodfurni');

db.payments.dropIndexes();

// --- 1. Index on orderId ---
db.payments.createIndex(
    { "orderId": 1 },
    { name: "orderId_idx" }
);

// --- 2. Index on status ---
db.payments.createIndex(
    { "status": 1 },
    { name: "status_idx" }
);

// --- 3. Index on transactionRef ---
db.payments.createIndex(
    { "transactionRef": 1 },
    { name: "transactionRef_idx" }
);

print("=== Payments Collection Indexes ===");
db.payments.getIndexes().forEach(function(idx) {
    print("Index: " + idx.name);
    printjson(idx.key);
    print("");
});
