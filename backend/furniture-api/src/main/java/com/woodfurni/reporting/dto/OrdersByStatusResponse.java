package com.woodfurni.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Order count grouped by status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdersByStatusResponse {

    private String status;
    private Long count;
}
