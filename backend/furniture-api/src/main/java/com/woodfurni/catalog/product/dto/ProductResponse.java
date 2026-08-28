package com.woodfurni.catalog.product.dto;

import com.woodfurni.catalog.product.enums.Dimensions;
import com.woodfurni.catalog.product.enums.ProductEnvironment;
import com.woodfurni.catalog.product.enums.ProductRoom;
import com.woodfurni.catalog.product.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private String id;
    private String sku;
    private String slug;
    private String name;
    private String categoryId;
    private String categoryName;
    private List<String> materialIds;
    private List<String> materialNames;
    private ProductEnvironment environment;
    private ProductRoom room;
    private Dimensions dimensions;
    private BigDecimal weight;
    private String color;
    private String finish;
    private BigDecimal price;
    private BigDecimal salePrice;
    private List<String> images;
    private String description;
    private String warranty;
    private ProductStatus status;
    private Double ratingAverage;
    private Integer ratingCount;
    private Instant createdAt;
    private Instant updatedAt;
}
