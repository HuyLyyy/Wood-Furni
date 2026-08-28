package com.woodfurni.review.dto;

import com.woodfurni.review.enums.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Enriched review row used by the admin moderation view
 * (GET /api/v1/admin/reviews).
 *
 * In addition to the standard Review fields, this projection carries
 * denormalized display data so the admin table can be rendered without
 * extra lookups:
 *   - productName     (resolved from ProductRepository)
 *   - userFullName    (resolved from UserRepository)
 *   - orderNumber     (resolved from OrderRepository)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReviewView {

    private String id;
    private String productId;
    private String productName;
    private String userId;
    private String userFullName;
    private String orderId;
    private String orderNumber;
    private Integer rating;
    private String comment;
    private ReviewStatus status;
    private Instant createdAt;
}