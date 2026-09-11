package com.woodfurni.inventory.bootstrap;

import com.woodfurni.catalog.product.model.Product;
import com.woodfurni.catalog.product.repository.ProductRepository;
import com.woodfurni.inventory.model.Inventory;
import com.woodfurni.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ensures every product has an inventory record on application startup.
 * Idempotent — safe to run on every boot.
 *
 * <p>Self-healing rules enforced on every boot:
 * <ol>
 *   <li>Ensure a MongoDB unique index on {@code inventories.productId}
 *       so the database itself rejects any future duplicate.</li>
 *   <li>Remove duplicate inventory documents for any productId, keeping the
 *       one with the latest {@code updatedAt}.</li>
 *   <li>Create one inventory record per product that doesn't have one yet,
 *       using an atomic upsert so concurrent boots cannot create duplicates.</li>
 * </ol>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class InventoryBootstrap {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final MongoTemplate mongoTemplate;

    @Bean
    public ApplicationRunner ensureAllProductsHaveInventory() {
        return args -> {
            // Step 1: enforce the unique constraint at the DB level.
            ensureUniqueProductIdIndex();

            // Step 2: clean up legacy duplicates (keeping the latest by updatedAt).
            healDuplicateInventories();

            // Step 3: ensure every product has at least one inventory record.
            int created = 0;
            int skipped = 0;
            List<Product> products = productRepository.findAll();
            for (Product p : products) {
                if (inventoryRepository.existsByProductId(p.getId())) {
                    skipped++;
                    continue;
                }

                // Atomic upsert: $setOnInsert writes only when inserting.
                // Combined with the unique index from Step 1, concurrent boots
                // cannot create duplicates.
                Query q = new Query(Criteria.where("productId").is(p.getId()));
                Update u = new Update()
                        .setOnInsert("productId", p.getId())
                        .setOnInsert("quantityOnHand", 0)
                        .setOnInsert("quantityReserved", 0)
                        .setOnInsert("lowStockThreshold", 5);
                try {
                    mongoTemplate.upsert(q, u, Inventory.class);
                    created++;
                    log.info("[InventoryBootstrap] Created inventory for product {} (id={})",
                            p.getName(), p.getId());
                } catch (DuplicateKeyException dup) {
                    // Another replica beat us to it — that's fine.
                    skipped++;
                }
            }
            log.info("[InventoryBootstrap] Done. Created {} inventory record(s), {} already existed.",
                    created, skipped);
        };
    }

    /**
     * Ensure a unique index exists on {@code inventories.productId}.
     *
     * <p>Setting {@code unique(true)} on the document field alone is NOT
     * enforced by MongoDB until the index is actually built. Without an
     * explicit {@code ensureIndex} call here, two concurrent boots could
     * still write duplicate documents.
     *
     * <p>If a unique index on this field already exists we skip creation.
     * If a non-unique one exists we drop it first (MongoDB does not allow
     * changing the uniqueness flag in place). Duplicate documents are
     * removed via {@link #healDuplicateInventories()} before creating the
     * unique index, since MongoDB refuses to build a unique index over
     * existing duplicates.
     */
    private void ensureUniqueProductIdIndex() {
        String collection = "inventories";
        boolean uniqueExists = false;
        boolean nonUniqueExists = false;

        for (var idx : mongoTemplate.indexOps(collection).getIndexInfo()) {
            if (idx.getName() == null) continue;
            for (var key : idx.getIndexFields()) {
                if (!"productId".equals(key.getKey())) continue;
                if (Boolean.TRUE.equals(idx.isUnique())) {
                    uniqueExists = true;
                } else {
                    nonUniqueExists = true;
                }
            }
        }

        if (uniqueExists) {
            log.info("[InventoryBootstrap] Unique index on productId already present.");
            return;
        }

        if (nonUniqueExists) {
            try {
                mongoTemplate.indexOps(collection).dropIndex("productId_1");
                log.info("[InventoryBootstrap] Dropped legacy non-unique index on productId.");
            } catch (Exception e) {
                log.warn("[InventoryBootstrap] Could not drop legacy non-unique index: {}", e.getMessage());
            }
        }

        // Heal duplicates FIRST so the unique index can be created.
        healDuplicateInventories();

        Index def = new Index()
                .on("productId", Sort.Direction.ASC)
                .unique()
                .named("uk_productId");

        try {
            mongoTemplate.indexOps(collection).ensureIndex(def);
            log.info("[InventoryBootstrap] Ensured unique index on productId.");
        } catch (Exception e) {
            log.warn("[InventoryBootstrap] Failed to create unique index on productId: {}", e.getMessage());
        }
    }

    /**
     * Remove duplicate inventory documents for the same productId.
     * When multiple documents share the same productId, this keeps the one
     * with the latest {@code updatedAt} timestamp and deletes all others.
     */
    private void healDuplicateInventories() {
        Map<String, List<Inventory>> grouped = inventoryRepository.findAll().stream()
                .collect(Collectors.groupingBy(Inventory::getProductId));

        int totalRemoved = 0;
        int productCount = 0;
        for (Map.Entry<String, List<Inventory>> e : grouped.entrySet()) {
            List<Inventory> copies = new ArrayList<>(e.getValue());
            if (copies.size() < 2) continue;

            productCount++;
            // Sort by updatedAt descending, keep the first, delete the rest.
            copies.sort((a, b) -> {
                Instant ta = a.getUpdatedAt() != null ? a.getUpdatedAt() : Instant.MIN;
                Instant tb = b.getUpdatedAt() != null ? b.getUpdatedAt() : Instant.MIN;
                return tb.compareTo(ta); // newest first
            });

            Inventory keeper = copies.get(0);
            List<Inventory> toDelete = copies.subList(1, copies.size());

            for (Inventory dup : toDelete) {
                inventoryRepository.deleteById(dup.getId());
                totalRemoved++;
                log.warn("[InventoryBootstrap] Removed duplicate inventory doc id={} for productId={} " +
                                "(keeper id={} updatedAt={})",
                        dup.getId(), e.getKey(), keeper.getId(), keeper.getUpdatedAt());
            }
        }

        if (totalRemoved > 0) {
            log.info("[InventoryBootstrap] Duplicate cleanup: removed {} document(s) for {} product(s)",
                    totalRemoved, productCount);
        } else {
            log.info("[InventoryBootstrap] Duplicate cleanup: no duplicates found.");
        }
    }
}
