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
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
     * Ensures every product has an inventory record on application startup.
     * Idempotent — safe to run on every boot.
     *
     * Reason: legacy products created before {@code ProductService.create()}
     * auto-inits inventory may not have stock tracking. This runner fills the
     * gap so checkout can always call {@code InventoryService.reserve()} without
     * encountering {@code EntityNotFoundException}.
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
                // Step 1: clean up duplicate inventory documents (keep latest by updatedAt).
                healDuplicateInventories();

                // Step 2: ensure every product has at least one inventory record.
                List<Product> products = productRepository.findAll();
                int created = 0;
                int skipped = 0;
                for (Product p : products) {
                    if (inventoryRepository.existsByProductId(p.getId())) {
                        skipped++;
                        continue;
                    }
                    Inventory inv = Inventory.builder()
                            .productId(p.getId())
                            .quantityOnHand(0)
                            .quantityReserved(0)
                            .lowStockThreshold(5)
                            .build();
                    inventoryRepository.save(inv);
                    created++;
                    log.info("[InventoryBootstrap] Created inventory for existing product {} (id={})",
                            p.getName(), p.getId());
                }
                log.info("[InventoryBootstrap] Done. Created {} inventory record(s), {} already existed.",
                        created, skipped);
            };
        }

        /**
         * Remove duplicate inventory documents for the same productId.
         * When multiple documents share the same productId, this keeps the one with
         * the latest {@code updatedAt} timestamp and deletes all others.
         *
         * This self-heals any accidental duplicates introduced by past bugs (e.g.
         * InventoryBootstrap running multiple times, or {@code initForProduct}
         * not being idempotent).
         *
         * Idempotent — after the first run there are no duplicates to clean up.
         */
        private void healDuplicateInventories() {
            String collection = "inventories";

            // Find all productIds that appear more than once.
            List<String> duplicateProductIds = inventoryRepository.findAll().stream()
                    .collect(Collectors.groupingBy(Inventory::getProductId))
                    .entrySet().stream()
                    .filter(e -> e.getValue().size() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            int totalRemoved = 0;
            for (String productId : duplicateProductIds) {
                List<Inventory> copies = new ArrayList<>(inventoryRepository.findByProductId(productId).stream()
                        .collect(Collectors.toList()));

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
                            dup.getId(), productId, keeper.getId(), keeper.getUpdatedAt());
                }
            }

            if (totalRemoved > 0) {
                log.info("[InventoryBootstrap] Duplicate cleanup: removed {} document(s) for {} product(s)",
                        totalRemoved, duplicateProductIds.size());
            } else {
                log.info("[InventoryBootstrap] Duplicate cleanup: no duplicates found.");
            }
        }
    }
