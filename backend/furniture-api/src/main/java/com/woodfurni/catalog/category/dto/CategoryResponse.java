package com.woodfurni.catalog.category.dto;

import com.woodfurni.catalog.category.model.CategoryEnvironment;
import com.woodfurni.catalog.category.model.CategoryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private String id;
    private String name;
    private String slug;
    private CategoryEnvironment environment;
    private String parentId;
    private Integer order;
    private CategoryStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
