package com.woodfurni.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewsResponse {

    private String productId;
    private String productName;
    private Double ratingAverage;
    private Integer ratingCount;
    private List<ReviewResponse> reviews;
    private Integer page;
    private Integer totalPages;
    private Long totalElements;
}
