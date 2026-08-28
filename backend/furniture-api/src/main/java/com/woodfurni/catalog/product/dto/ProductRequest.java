package com.woodfurni.catalog.product.dto;

import com.woodfurni.catalog.product.enums.Dimensions;
import com.woodfurni.catalog.product.enums.ProductEnvironment;
import com.woodfurni.catalog.product.enums.ProductRoom;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "SKU is required")
    private String sku;

    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 200, message = "Name must be between 3 and 200 characters")
    private String name;

    private String slug;

    @NotBlank(message = "Category ID is required")
    private String categoryId;

    private List<String> materialIds;

    @NotNull(message = "Environment is required")
    private ProductEnvironment environment;

    private ProductRoom room;

    private Dimensions dimensions;

    @Positive(message = "Weight must be positive")
    private BigDecimal weight;

    private String color;

    private String finish;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    @DecimalMin(value = "0.0", message = "Sale price cannot be negative")
    private BigDecimal salePrice;

    @Size(min = 1, message = "At least one image is required for active products")
    private List<String> images;

    private String description;

    private String warranty;

    private String status;
}
