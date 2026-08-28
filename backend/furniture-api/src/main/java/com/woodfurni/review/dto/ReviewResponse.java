package com.woodfurni.review.dto;

import com.woodfurni.review.enums.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private String id;
    private String productId;
    private String userId;
    private String orderId;
    private Integer rating;
    private String comment;
    private ReviewStatus status;
    private Instant createdAt;
    private String userDisplayName;
}
