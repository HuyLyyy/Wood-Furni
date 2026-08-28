package com.woodfurni.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request payload for the "Nhận lại hàng" action when SHIPPING → DELIVERED.
 *
 * Business rules:
 * - If all items have receivedQuantity = 0 → order is CANCELLED (customer rejected all)
 * - If any item has receivedQuantity > 0 → order is DELIVERED with adjusted quantities
 * - receivedQuantity must be between 0 and the ordered quantity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiveReturnRequest {

    /**
     * List of received quantities per order item.
     * Each entry corresponds to an item in the order by index.
     * - 0 = customer rejected this item
     * - > 0 = quantity the customer actually accepted (≤ original quantity)
     */
    @NotNull(message = "Item quantities are required")
    @Size(min = 1, message = "At least one item is required")
    private List<ItemReceipt> items;

    /**
     * Optional note explaining why items were rejected/partial.
     */
    private String note;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemReceipt {
        @NotNull(message = "Item index is required")
        private Integer itemIndex;

        @NotNull(message = "Received quantity is required")
        private Integer receivedQuantity;
    }
}
