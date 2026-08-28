package com.woodfurni.review.repository;

import com.woodfurni.review.enums.ReviewStatus;
import com.woodfurni.review.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends MongoRepository<Review, String> {

    Page<Review> findByProductIdAndStatus(String productId, ReviewStatus status, Pageable pageable);

    Page<Review> findByProductId(String productId, Pageable pageable);

    Page<Review> findByStatus(ReviewStatus status, Pageable pageable);

    List<Review> findByProductIdAndStatus(String productId, ReviewStatus status);

    long countByProductIdAndStatus(String productId, ReviewStatus status);

    Optional<Review> findByUserIdAndProductIdAndOrderId(String userId, String productId, String orderId);

    boolean existsByUserIdAndProductIdAndOrderId(String userId, String productId, String orderId);

    double getAverageRatingByProductId(String productId);
}
