package com.woodfurni.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request hủy chuyến xe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelTripRequest {
    /** Lý do hủy (optional). */
    private String reason;
}
