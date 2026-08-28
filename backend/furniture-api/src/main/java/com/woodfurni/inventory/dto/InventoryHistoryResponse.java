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
public class InventoryHistoryResponse {

    private String id;
    private String inventoryId;
    private String productId;

    /** Positive = stock in (restock), negative = stock out. */
    private int delta;

    /** Stock before this change. */
    private int previousQuantity;

    /** Stock after this change. */
    private int newQuantity;

    /** Human-readable actor label. */
    private String actorName;

    /** UserId of the actor (null for system operations). */
    private String actorUserId;

    /** Reason for manual adjustments. */
    private String reason;

    /** MANUAL_ADJUST | RESERVE | RELEASE | COMMIT */
    private String operationType;

    private Instant createdAt;
}
