package com.woodfurni.catalog.product.model;

import com.woodfurni.catalog.product.enums.Dimensions;
import com.woodfurni.catalog.product.enums.ProductEnvironment;
import com.woodfurni.catalog.product.enums.ProductRoom;
import com.woodfurni.catalog.product.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Product entity for furniture items.
 * Collection: "products"
 *
 * As defined in WOODFURNI spec Mục 3.3.
 *
 * Indexes:
 * - { sku: 1 } unique
 * - { slug: 1 } unique
 * - { categoryId: 1, environment: 1, status: 1 }
 * - { price: 1 }
 * - text index on name + description (for keyword search)
 * - { environment: 1, room: 1, materialIds: 1, price: 1 } (compound for multi-filter)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
@CompoundIndexes({
    @CompoundIndex(name = "category_env_status_idx", def = "{'categoryId': 1, 'environment': 1, 'status': 1}"),
    @CompoundIndex(name = "env_room_material_price_idx", def = "{'environment': 1, 'room': 1, 'materialIds': 1, 'price': 1}")
})
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;

    @Indexed(unique = true)
    private String slug;

    @TextIndexed(weight = 3)
    private String name;

    private String categoryId;

    private List<String> materialIds;

    private ProductEnvironment environment;

    private ProductRoom room;

    private Dimensions dimensions;

    private BigDecimal weight;

    private String color;

    private String finish;

    @Indexed
    private BigDecimal price;

    private BigDecimal salePrice;

    private List<String> images;

    @TextIndexed
    private String description;

    private String warranty;

    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    @Builder.Default
    private Double ratingAverage = 0.0;

    @Builder.Default
    private Integer ratingCount = 0;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
