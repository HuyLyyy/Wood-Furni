// WOODFURNI - MongoDB Index Scripts for Products Collection
// Run this script with: mongosh < database/indexes/products.indexes.js
// Or import via MongoDB Compass / MongoDB Shell

use('woodfurni');

// ============================================================
// COLLECTION: products
// As defined in WOODFURNI spec Mục 3.3 - Product indexes
// ============================================================

// Drop existing indexes (optional - only run if you want to recreate)
db.products.dropIndexes();

// --- 1. Unique indexes ---
db.products.createIndex({ "sku": 1 }, { unique: true, name: "sku_unique" });
db.products.createIndex({ "slug": 1 }, { unique: true, sparse: true, name: "slug_unique" });

// --- 2. Compound indexes for filtering ---
// For filter by category + environment + status (admin/management pages)
db.products.createIndex(
    { "categoryId": 1, "environment": 1, "status": 1 },
    { name: "category_env_status_idx" }
);

// For multi-filter: environment + room + materialIds + price (complex filter UI)
db.products.createIndex(
    { "environment": 1, "room": 1, "materialIds": 1, "price": 1 },
    { name: "env_room_material_price_idx" }
);

// --- 3. Single-field indexes ---
db.products.createIndex({ "price": 1 }, { name: "price_asc" });
db.products.createIndex({ "status": 1 }, { name: "status_idx" });
db.products.createIndex({ "createdAt": -1 }, { name: "createdAt_desc" });
db.products.createIndex({ "ratingAverage": -1 }, { name: "ratingAverage_desc" });

// --- 4. Text index for keyword search ---
// Supports: GET /api/v1/products?keyword=bàn ăn
// Searches across name (weight 3) and description (weight 1)
// Note: MongoDB text search does NOT handle Vietnamese diacritics well.
//       For production, consider Atlas Search or a dedicated search engine (Elasticsearch, MeiliSearch).
//       This is a known limitation documented for the thesis.
db.products.createIndex(
    { "name": "text", "description": "text" },
    {
        name: "text_search_idx",
        weights: {
            name: 3,
            description: 1
        },
        default_language: "none"
    }
);

// --- 5. Index for inventory reference ---
db.products.createIndex({ "_id": 1 }, { name: "_id_ref" });

// ============================================================
// Verification: list all indexes created
// ============================================================
print("=== Products Collection Indexes ===");
db.products.getIndexes().forEach(function(index) {
    print("Index: " + index.name);
    printjson(index.key);
    print("");
});
