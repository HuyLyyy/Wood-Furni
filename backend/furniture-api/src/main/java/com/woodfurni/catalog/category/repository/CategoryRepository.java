package com.woodfurni.catalog.category.repository;

import com.woodfurni.catalog.category.model.Category;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {

    Optional<Category> findBySlug(String slug);

    List<Category> findByParentIdIsNull();

    List<Category> findByParentId(String parentId);

    List<Category> findByEnvironment(com.woodfurni.catalog.category.model.CategoryEnvironment environment);

    boolean existsBySlug(String slug);
}
