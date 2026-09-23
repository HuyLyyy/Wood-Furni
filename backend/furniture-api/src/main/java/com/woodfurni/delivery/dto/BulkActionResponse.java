package com.woodfurni.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response cho bulk action — thống kê thành công/thất bại.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkActionResponse {
    private int successCount;
    private int failureCount;
    
    @Builder.Default
    private List<String> successIds = new ArrayList<>();
    
    @Builder.Default
    private List<String> failureIds = new ArrayList<>();
    
    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
