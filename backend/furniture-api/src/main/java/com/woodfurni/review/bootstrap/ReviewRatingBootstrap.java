package com.woodfurni.review.bootstrap;

import com.woodfurni.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On application startup, recompute {@code ratingCount} and {@code ratingAverage}
 * for every product from the actual reviews collection.
 *
 * Why this exists:
 *   Seed data (database/sample-data/seed.js) hardcoded fake rating counts
 *   like 12, 8, 15, ... on each product document, but no corresponding reviews
 *   were ever inserted. The result was the product detail page showing
 *   "Đánh giá (12)" even for products with zero real reviews — and the
 *   ProductCard on the listing page proudly displaying "(12)" next to the
 *   stars. This runner heals the denormalised fields so the on-screen badge
 *   always reflects the truth.
 *
 * Idempotent — only writes when value differs.
 */
@Slf4j
@Component
@Order(10) // run after InventoryBootstrap (Order(0) by default) so product state is settled
@RequiredArgsConstructor
public class ReviewRatingBootstrap implements ApplicationRunner {

    private final ReviewService reviewService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            reviewService.recalculateAllProductRatings();
        } catch (Exception ex) {
            // Never fail boot because of a ratings self-heal — log and move on.
            log.warn("[RatingBootstrap] Failed to heal product ratings on startup: {}",
                    ex.getMessage());
        }
    }
}