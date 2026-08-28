package com.woodfurni.catalog.material.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Material entity for product material types (e.g., Oak, Walnut, Teak).
 * Collection: "materials"
 *
 * As defined in WOODFURNI spec Mục 3.3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "materials")
public class Material {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    @Indexed(unique = true)
    private String code;

    private String description;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
