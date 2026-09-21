package com.woodfurni.inventory.repository;

import com.woodfurni.inventory.model.InventoryHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryHistoryRepository extends MongoRepository<InventoryHistory, String> {

    /**
     * Return paginated history entries for an inventory document, newest first.
     */
    Page<InventoryHistory> findByInventoryIdOrderByCreatedAtDesc(String inventoryId, Pageable pageable);

    /**
     * Return paginated history entries for a product, newest first.
     */
    Page<InventoryHistory> findByProductIdOrderByCreatedAtDesc(String productId, Pageable pageable);

    /**
     * Look up the most recent history record that references the given
     * stored evidence filename (the on-disk UUID). Used by the download
     * endpoint to recover the original user-friendly filename.
     */
    java.util.Optional<InventoryHistory> findFirstByEvidenceFileNameOrderByCreatedAtDesc(String evidenceFileName);
}
