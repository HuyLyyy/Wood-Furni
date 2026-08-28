package com.woodfurni.catalog.product.repository;

import com.woodfurni.catalog.product.enums.ProductEnvironment;
import com.woodfurni.catalog.product.enums.ProductStatus;
import com.woodfurni.catalog.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySku(String sku);

    Optional<Product> findBySlug(String slug);

    boolean existsBySku(String sku);

    boolean existsBySlug(String slug);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    List<Product> findByCategoryId(String categoryId);

    Page<Product> findByCategoryIdAndStatus(String categoryId, ProductStatus status, Pageable pageable);

    List<Product> findByMaterialIdsContaining(String materialId);

    List<Product> findTop10ByOrderByRatingAverageDesc();
}
