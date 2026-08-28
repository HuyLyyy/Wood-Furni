package com.woodfurni.inventory.repository;

import com.woodfurni.inventory.model.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends MongoRepository<Inventory, String> {

    Optional<Inventory> findByProductId(String productId);

    boolean existsByProductId(String productId);

    Page<Inventory> findByQuantityOnHandLessThanEqual(Integer threshold, Pageable pageable);

    Page<Inventory> findAllByQuantityOnHandLessThanEqual(Integer lowStockThreshold, Pageable pageable);
}
