package com.woodfurni.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for POST /api/v1/orders/{id}/tracking-updates.
 *
 * Posted by SALES / WAREHOUSE / ADMIN while the order is SHIPPING.
 *
 * - {@code isDelivered=true} additionally triggers SHIPPING → DELIVERED
 *   transition (inventory commit, status history entry, customer notify).
 * - {@code isDelivered=false} (default) just appends a timeline entry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingUpdateRequest {

    /** Short status label: "Đã lấy hàng", "Đang vận chuyển", "Đến kho phân loại", "Đang giao", ... */
    @NotBlank
    @Size(max = 100)
    private String status;

    /** Current shipment location, e.g. "Kho HN", "Quận 1, HCM". */
    @Size(max = 255)
    private String location;

    /** Optional free-text note. */
    @Size(max = 1000)
    private String note;

    /**
     * When true, this tracking entry marks delivery complete and triggers
     * the SHIPPING → DELIVERED transition in the same call. Defaults to false.
     */
    private Boolean isDelivered;
}