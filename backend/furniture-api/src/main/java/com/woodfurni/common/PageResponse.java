package com.woodfurni.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Standardized paginated response wrapper.
 * Used for list endpoints with pagination as defined in WOODFURNI spec Mục 3.4.
 *
 * Format:
 * { "success": true, "message": "OK",
 *   "data": { "items": [ ... ], "page": 0, "size": 20, "totalElements": 120, "totalPages": 6 },
 *   "timestamp": "..." }
 *
 * @param <T> The type of items in the list
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    /**
     * Creates a PageResponse from a Spring Data Page object.
     */
    public static <T> PageResponse<T> from(
            org.springframework.data.domain.Page<T> page) {
        return PageResponse.<T>builder()
                .items(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
