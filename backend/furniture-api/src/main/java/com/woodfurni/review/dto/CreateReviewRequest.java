package com.woodfurni.review.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating a review.
 * Customer must have a delivered order containing the product.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewRequest {

    @NotBlank(message = "Order ID is required")
    private String orderId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    private Integer rating;

    @Size(max = 2000, message = "Comment cannot exceed 2000 characters")
    private String comment;
}
