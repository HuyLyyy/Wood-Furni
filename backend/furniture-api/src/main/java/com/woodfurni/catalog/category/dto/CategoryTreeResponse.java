package com.woodfurni.catalog.category.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryTreeResponse {

    private String id;
    private String name;
    private String slug;
    private String environment;
    private Integer order;
    private List<CategoryTreeResponse> children;
}
