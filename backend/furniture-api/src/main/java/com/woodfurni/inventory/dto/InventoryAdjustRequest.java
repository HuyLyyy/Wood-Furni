package com.woodfurni.inventory.dto;

import com.woodfurni.inventory.enums.AdjustmentReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Manual stock adjustment request.
 *
 * Sent as multipart/form-data so a single call can carry:
 *   - {@code reasonCode}  : one of {@link AdjustmentReason}
 *   - {@code delta}       : signed stock change (positive = restock, negative = deduction)
 *   - {@code note}        : optional free-form remark
 *   - {@code evidence}    : REQUIRED Excel evidence file (.xlsx / .xls, max 10MB)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustRequest {

    /** Must be one of the enum constants from {@link AdjustmentReason}. */
    @NotNull(message = "Reason code is required")
    private AdjustmentReason reasonCode;

    @NotNull(message = "Delta is required")
    @Min(value = -10000, message = "Delta out of range")
    private Integer delta;

    /** Optional free-form remark from staff. Persists for context. */
    private String note;
}

