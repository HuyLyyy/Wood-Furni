package com.woodfurni.order.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Immutable status change record appended to statusHistory.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistoryEntry {

    private String status;

    private Instant changedAt;

    private String changedBy;

    /**
     * Optional human-readable note attached to this status change
     * (e.g. customer's cancellation reason).
     */
    private String note;
}
