package com.woodfurni.review.service;

import com.woodfurni.auth.model.User;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.catalog.product.model.Product;
import com.woodfurni.catalog.product.repository.ProductRepository;
import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.order.enums.OrderStatus;
import com.woodfurni.order.model.Order;
import com.woodfurni.order.model.OrderItem;
import com.woodfurni.order.repository.OrderRepository;
import com.woodfurni.review.dto.*;
import com.woodfurni.review.enums.ReviewStatus;
import com.woodfurni.review.exception.DuplicateReviewException;
import com.woodfurni.review.exception.OrderNotFoundException;
import com.woodfurni.review.exception.OrderOwnershipException;
import com.woodfurni.review.model.Review;
import com.woodfurni.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Review management service.
 *
 * Validation chain for create():
 * 1. Order exists → else: "Order not found"
 * 2. Order belongs to user → else: "Order does not belong to this user"
 * 3. Order is DELIVERED → else: "Only delivered orders can be reviewed"
 * 4. Order contains this product → else: "This product was not in this order"
 * 5. Not already reviewed (userId+productId+orderId unique) → else: "You have already reviewed this product for this order"
 *
 * After successful create/update:
 * - Recalculate Product.ratingAverage and Product.ratingCount from VISIBLE reviews
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * Create a review. Validates all preconditions before saving.
     *
     * Validation chain (strict order — each throws typed exception with specific HTTP status):
     * 1. Order exists                     → 404 OrderNotFoundException
     * 2. Order belongs to authenticated user → 403 OrderOwnershipException
     * 3. Order is DELIVERED               → 400 IllegalArgumentException
     * 4. Product exists in Order.items     → 400 IllegalArgumentException
     * 5. No duplicate review              → 409 DuplicateReviewException
     *
     * The customerId is taken ONLY from SecurityContext — never trusted from request body.
     */
    @Transactional
    public ReviewResponse create(String userId, String productId,
                                  String orderId, Integer rating, String comment) {
        // --- CHECK 1: Order must exist → 404 ---
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // --- CHECK 2: Order must belong to this authenticated user → 403 ---
        if (!order.getCustomerId().equals(userId)) {
            throw new OrderOwnershipException();
        }

        // --- CHECK 3: Order must be DELIVERED → 400 ---
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException(
                    "Only delivered orders can be reviewed. Current status: " + order.getStatus().name());
        }

        // --- CHECK 4: Product must exist in Order.items → 400 ---
        boolean productInOrder = order.getItems().stream()
                .anyMatch(item -> item.getProductId().equals(productId));
        if (!productInOrder) {
            throw new IllegalArgumentException(
                    "This product was not in order " + order.getOrderNumber());
        }

        // --- CHECK 5: No duplicate review → 409 ---
        if (reviewRepository.existsByUserIdAndProductIdAndOrderId(userId, productId, orderId)) {
            throw new DuplicateReviewException();
        }

        // --- Create review ---
        Review review = Review.builder()
                .productId(productId)
                .userId(userId)
                .orderId(orderId)
                .rating(rating)
                .comment(comment)
                .status(ReviewStatus.PUBLISHED)
                .build();

        Review saved = reviewRepository.save(review);
        log.info("Review created: id={}, productId={}, userId={}", saved.getId(), productId, userId);

        // --- Update denormalized rating fields on Product ---
        recalculateProductRating(productId);

        return toResponse(saved);
    }

    /**
     * Update review status (admin only).
     * When hiding/showing, recalculate product rating.
     */
    @Transactional
    public ReviewResponse updateStatus(String reviewId, ReviewStatus newStatus) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found: " + reviewId));

        ReviewStatus oldStatus = review.getStatus();
        review.setStatus(newStatus);
        Review saved = reviewRepository.save(review);

        // Recalculate product rating if visibility changed
        if (oldStatus != newStatus) {
            recalculateProductRating(review.getProductId());
        }

        return toResponse(saved);
    }

    /**
     * List visible reviews for a product (public).
     * Only VISIBLE reviews are returned.
     *
     * The returned {@code ratingAverage} and {@code ratingCount} are computed
     * from the actual reviews collection (not from the denormalised
     * {@code product.ratingCount} field). This way the product detail page
     * never displays a stale "Đánh giá (12)" badge for a product that has
     * zero reviews — the value stays in sync with what the customer can
     * actually see on the reviews list below it.
     */
    public ProductReviewsResponse listByProduct(String productId, int page, int size) {
        Product product = productRepository.findById(productId).orElse(null);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> reviewPage = reviewRepository.findByProductIdAndStatus(
                productId, ReviewStatus.PUBLISHED, pageable);

        List<ReviewResponse> reviews = reviewPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        // Compute rating stats from the FULL set of published reviews, not the
        // current page slice — otherwise the average would shift depending on
        // which page the caller asked for.
        int totalCount = (int) reviewPage.getTotalElements();
        double average = 0.0;
        if (totalCount > 0) {
            List<Review> allPublished = reviewRepository
                    .findByProductIdAndStatus(productId, ReviewStatus.PUBLISHED);
            double sum = allPublished.stream()
                    .mapToInt(r -> r.getRating() != null ? r.getRating() : 0)
                    .sum();
            average = Math.round((sum / totalCount) * 10.0) / 10.0;
        }

        ProductReviewsResponse.ProductReviewsResponseBuilder builder = ProductReviewsResponse.builder()
                .productId(productId)
                .productName(product != null ? product.getName() : null)
                .ratingAverage(average)
                .ratingCount(totalCount)
                .reviews(reviews)
                .page(page)
                .totalPages(reviewPage.getTotalPages())
                .totalElements(reviewPage.getTotalElements());

        return builder.build();
    }

    public ReviewResponse getById(String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found: " + reviewId));
        return toResponse(review);
    }

    /**
     * System-wide review listing for the admin moderation UI.
     *
     * Filters:
     *   - rating    (optional, 1-5)
     *   - status    (optional, PUBLISHED|HIDDEN)
     *   - productId (optional)
     *
     * Always returns enriched rows (productName, userFullName,
     * orderNumber) so the admin table can be rendered without N+1 calls.
     *
     * Implementation: we do an unfiltered find() through the repository to
     * keep this simple, then apply filters in-memory. For a thesis-scale
     * dataset this is acceptable; the methods are still O(N) but bounded
     * by the page size.
     */
    public Page<AdminReviewView> listForAdmin(Integer rating, ReviewStatus status,
                                                String productId, Pageable pageable) {
        // First, decide whether to use a query method or fall back to findAll.
        Page<Review> page;
        boolean hasRating = rating != null;
        boolean hasStatus = status != null;
        boolean hasProduct = productId != null && !productId.isBlank();

        if (!hasRating && !hasStatus && !hasProduct) {
            page = reviewRepository.findAll(pageable);
        } else if (hasRating && !hasStatus && !hasProduct) {
            page = filterByRating(rating, pageable);
        } else if (!hasRating && hasStatus && !hasProduct) {
            page = reviewRepository.findByStatus(status, pageable);
        } else if (!hasRating && !hasStatus) {
            page = reviewRepository.findByProductId(productId, pageable);
        } else {
            // Compound filter — use repository find-all then filter.
            page = reviewRepository.findAll(pageable);
        }

        List<Review> filtered = page.getContent();
        if (hasRating) {
            int r = rating;
            filtered = filtered.stream().filter(x -> x.getRating() != null && x.getRating() == r).toList();
        }
        if (hasStatus) {
            filtered = filtered.stream().filter(x -> status.equals(x.getStatus())).toList();
        }
        if (hasProduct) {
            filtered = filtered.stream().filter(x -> productId.equals(x.getProductId())).toList();
        }
        // If filtering shrank the result, treat total as the filtered list size.
        long total = (hasRating || hasStatus || hasProduct)
                ? filtered.size() : page.getTotalElements();

        Set<String> productIds = new HashSet<>();
        Set<String> userIds = new HashSet<>();
        Set<String> orderIds = new HashSet<>();
        for (Review r : filtered) {
            if (r.getProductId() != null) productIds.add(r.getProductId());
            if (r.getUserId() != null) userIds.add(r.getUserId());
            if (r.getOrderId() != null) orderIds.add(r.getOrderId());
        }

        Map<String, String> productNames = new HashMap<>();
        for (Product p : productRepository.findAllById(productIds)) {
            productNames.put(p.getId(), p.getName());
        }
        Map<String, String> userNames = new HashMap<>();
        for (User u : userRepository.findAllById(userIds)) {
            userNames.put(u.getId(), u.getFullName());
        }
        Map<String, String> orderNumbers = new HashMap<>();
        for (Order o : orderRepository.findAllById(orderIds)) {
            orderNumbers.put(o.getId(), o.getOrderNumber());
        }

        final Map<String, String> pn = productNames;
        final Map<String, String> un = userNames;
        final Map<String, String> on = orderNumbers;

        List<AdminReviewView> rows = filtered.stream().map(r -> AdminReviewView.builder()
                .id(r.getId())
                .productId(r.getProductId())
                .productName(pn.get(r.getProductId()))
                .userId(r.getUserId())
                .userFullName(un.get(r.getUserId()))
                .orderId(r.getOrderId())
                .orderNumber(on.get(r.getOrderId()))
                .rating(r.getRating())
                .comment(r.getComment())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build()
        ).collect(Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(rows, pageable, total);
    }

    /**
     * Rating-only filter. Because Review doesn't have a single-key method
     * `findByRating`, we delegate via findAll on the template path is heavy
     * — instead use a simple count + slice through the repository.
     */
    private Page<Review> filterByRating(int rating, Pageable pageable) {
        // Walk through pages until we accumulate enough. For an admin list
        // this is acceptable. The lazy approach keeps the implementation
        // explicit without requiring an extra repository method.
        List<Review> all = new java.util.ArrayList<>();
        int pageSize = 1000;
        int p = 0;
        org.springframework.data.domain.Page<Review> src;
        do {
            src = reviewRepository.findAll(
                    PageRequest.of(p, pageSize,
                            org.springframework.data.domain.Sort.by(
                                    org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));
            for (Review r : src) {
                if (r.getRating() != null && r.getRating() == rating) {
                    all.add(r);
                }
            }
            p++;
        } while (src.hasNext() && p < 50); // hard cap to avoid runaway
        // Manual slice for the requested page
        int from = (int) pageable.getOffset();
        int to = Math.min(all.size(), from + pageable.getPageSize());
        List<Review> slice = from >= all.size() ? List.of() : all.subList(from, to);
        return new org.springframework.data.domain.PageImpl<>(slice, pageable, all.size());
    }

    /**
     * Recalculate ratingAverage and ratingCount on Product from all VISIBLE reviews.
     * Called after every review create / status change.
     *
     * Strategy: simple re-query and compute average (not optimized incremental).
     */
    private void recalculateProductRating(String productId) {
        List<Review> visibleReviews = reviewRepository
                .findByProductIdAndStatus(productId, ReviewStatus.PUBLISHED);

        int count = visibleReviews.size();
        double average = 0.0;

        if (count > 0) {
            double sum = visibleReviews.stream()
                    .mapToInt(r -> r.getRating() != null ? r.getRating() : 0)
                    .sum();
            average = Math.round((sum / (double) count) * 10.0) / 10.0;
        }

        // Snapshot for the lambda — must be effectively final.
        final double finalAverage = average;
        final int finalCount = count;

        productRepository.findById(productId).ifPresent(product -> {
            product.setRatingCount(finalCount);
            product.setRatingAverage(finalAverage);
            productRepository.save(product);
            log.info("Product {} rating updated: avg={}, count={}", productId, finalAverage, finalCount);
        });
    }

    /**
     * Recalculate rating stats for EVERY product. Intended to be called once
     * on application startup (see {@code ReviewRatingBootstrap}) to heal any
     * drift between the denormalised {@code Product.ratingCount} and the
     * actual review collection — e.g. when seed data hardcoded fake counts.
     */
    @Transactional
    public void recalculateAllProductRatings() {
        List<Product> all = productRepository.findAll();
        int healed = 0;
        for (Product p : all) {
            String productId = p.getId();
            List<Review> visible = reviewRepository
                    .findByProductIdAndStatus(productId, ReviewStatus.PUBLISHED);

            int newCount = visible.size();
            double newAvg = 0.0;
            if (newCount > 0) {
                double sum = visible.stream()
                        .mapToInt(r -> r.getRating() != null ? r.getRating() : 0)
                        .sum();
                newAvg = Math.round((sum / (double) newCount) * 10.0) / 10.0;
            }

            Integer oldCount = p.getRatingCount() != null ? p.getRatingCount() : 0;
            Double oldAvg = p.getRatingAverage() != null ? p.getRatingAverage() : 0.0;

            if (oldCount != newCount || Double.compare(oldAvg, newAvg) != 0) {
                p.setRatingCount(newCount);
                p.setRatingAverage(newAvg);
                productRepository.save(p);
                healed++;
                log.info("[RatingBootstrap] Healed product {}: count {} → {}, avg {} → {}",
                        productId, oldCount, newCount, oldAvg, newAvg);
            }
        }
        log.info("[RatingBootstrap] Done. {} product(s) healed out of {}.",
                healed, all.size());
    }

    private ReviewResponse toResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProductId())
                .userId(review.getUserId())
                .orderId(review.getOrderId())
                .rating(review.getRating())
                .comment(review.getComment())
                .status(review.getStatus())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
