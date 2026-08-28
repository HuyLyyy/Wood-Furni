package com.woodfurni.catalog.material.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Code is required")
    @Pattern(regexp = "^[A-Z0-9_]{2,10}$", message = "Code must be 2-10 uppercase letters, numbers, or underscores (e.g., OAK, WALNUT, TEAK)")
    private String code;

    private String description;
}
