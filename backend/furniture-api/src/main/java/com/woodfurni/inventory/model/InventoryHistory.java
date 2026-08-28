package com.woodfurni.inventory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Audit trail for inventory mutations.
 *
 * Every stock operation (manual adjust, reserve, release, commit) writes one
 * entry so staff can audit exactly who changed what and when.
 *
 * Collection: "inventory_histories"
 * Index: (inventoryId, createdAt) for efficient per-product history queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "inventory_histories")
@CompoundIndex(name = "idx_inv_created", def = "{'inventoryId': 1, 'createdAt': -1}")
public class InventoryHistory {

    @Id
    private String id;

    /** Reference to the Inventory document _id. */
    @Indexed
    private String inventoryId;

    /** Reference to the Product id — denormalised for convenience. */
    @Indexed
    private String productId;

    /**
     * Change applied by this entry.
     * Positive = stock in (restock), negative = stock out (damage / commit / reserve).
     */
    private int delta;

    /** Stock level before this entry was applied. */
    private int previousQuantity;

    /** Stock level after this entry was applied. */
    private int newQuantity;

    /**
     * Who performed the change.
     * For manual adjustments: fullName (e.g. "Lê Văn Kho - WAREHOUSE").
     * For system operations: the system label (e.g. "Hệ thống - Checkout").
     */
    private String actorName;

    /** ID of the user who triggered the change (null for system operations). */
    private String actorUserId;

    /** Why the change was made. Only populated for manual adjustments. */
    private String reason;

    /** One of: MANUAL_ADJUST, RESERVE, RELEASE, COMMIT. */
    private String operationType;

    private Instant createdAt;
}
