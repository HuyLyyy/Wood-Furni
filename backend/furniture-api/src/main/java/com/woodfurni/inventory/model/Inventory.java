package com.woodfurni.inventory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Inventory entity for tracking product stock levels.
 * Collection: "inventories"
 *
 * quantityAvailable = quantityOnHand - quantityReserved (computed in service, NOT stored).
 *
 * As defined in WOODFURNI spec Mục 3.3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "inventories")
public class Inventory {

    @Id
    private String id;

    @Indexed(unique = true)
    private String productId;

    @Builder.Default
    private Integer quantityOnHand = 0;

    @Builder.Default
    private Integer quantityReserved = 0;

    @Builder.Default
    private Integer lowStockThreshold = 5;

    @LastModifiedDate
    private Instant updatedAt;
}
