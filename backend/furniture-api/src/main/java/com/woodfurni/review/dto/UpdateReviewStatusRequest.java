package com.woodfurni.review.dto;

import com.woodfurni.review.enums.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for updating review status (admin only).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReviewStatusRequest {

    private ReviewStatus status;
}
