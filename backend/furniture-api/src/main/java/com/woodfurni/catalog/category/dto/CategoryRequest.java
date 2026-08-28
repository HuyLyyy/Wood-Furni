package com.woodfurni.catalog.category.dto;

import com.woodfurni.catalog.category.model.CategoryEnvironment;
import com.woodfurni.catalog.category.model.CategoryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String slug;

    @NotNull(message = "Environment is required")
    private CategoryEnvironment environment;

    private String parentId;

    @Builder.Default
    private Integer order = 0;

    @Builder.Default
    private CategoryStatus status = CategoryStatus.ACTIVE;
}
