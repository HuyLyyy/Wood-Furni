package com.woodfurni.catalog.material.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialResponse {

    private String id;
    private String name;
    private String code;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
