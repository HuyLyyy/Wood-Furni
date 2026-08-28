package com.woodfurni.catalog.material.repository;

import com.woodfurni.catalog.material.model.Material;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialRepository extends MongoRepository<Material, String> {

    Optional<Material> findByName(String name);

    Optional<Material> findByCode(String code);

    List<Material> findByNameContainingIgnoreCase(String name);

    boolean existsByName(String name);

    boolean existsByCode(String code);
}
