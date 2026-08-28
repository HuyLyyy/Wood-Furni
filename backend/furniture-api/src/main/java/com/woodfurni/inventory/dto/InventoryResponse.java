package com.woodfurni.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {

    private String id;
    private String productId;
    private String productName;
    private String productSku;
    private Integer quantityOnHand;
    private Integer quantityReserved;
    private Integer quantityAvailable;
    private Integer lowStockThreshold;
    private Boolean isLowStock;
    private Instant updatedAt;
}
