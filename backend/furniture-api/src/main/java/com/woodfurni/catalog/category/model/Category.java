package com.woodfurni.catalog.category.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Category entity for product categorization.
 * Supports hierarchical tree via parentId (self-reference).
 * Collection: "categories"
 *
 * As defined in WOODFURNI spec Mục 3.3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "categories")
@CompoundIndex(name = "env_parent_idx", def = "{'environment': 1, 'parentId': 1}")
public class Category {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String slug;

    private CategoryEnvironment environment;

    private String parentId;

    @Builder.Default
    private Integer order = 0;

    @Builder.Default
    private CategoryStatus status = CategoryStatus.ACTIVE;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
