package com.woodfurni.review.model;

import com.woodfurni.review.enums.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Product review entity.
 * Collection: "reviews"
 *
 * Rules:
 * - Only customers who purchased the product (order DELIVERED) can review
 * - One review per user per product per order (unique compound index)
 * - ratingAverage / ratingCount on Product are denormalized fields updated
 *   after every successful create / status change
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reviews")
@CompoundIndexes({
    @CompoundIndex(name = "product_createdAt_idx", def = "{'productId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "user_product_order_unique_idx",
                   def = "{'userId': 1, 'productId': 1, 'orderId': 1}",
                   unique = true)
})
public class Review {

    @Id
    private String id;

    private String productId;

    private String userId;

    private String orderId;

    private Integer rating;

    private String comment;

    @Builder.Default
    private ReviewStatus status = ReviewStatus.PUBLISHED;

    @CreatedDate
    private Instant createdAt;
}
