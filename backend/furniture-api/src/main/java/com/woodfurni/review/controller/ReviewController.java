package com.woodfurni.review.controller;

import com.woodfurni.common.ApiResponse;
import com.woodfurni.common.PageResponse;
import com.woodfurni.review.dto.*;
import com.woodfurni.review.enums.ReviewStatus;
import com.woodfurni.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
// NOTE: unlike other controllers in this project (which mount at root paths like
// /orders, /products, /cart, /auth), this controller used to live under /api/v1.
// That mismatch caused the admin reviews page to 404 (NoResourceFoundException)
// because the FE was calling /admin/reviews without the /api/v1 prefix. We
// mount at root to match the rest of the surface area. If a future deployment
// introduces a global /api/v1 prefix via server.servlet.context-path or a
// gateway, adjust both this annotation and the FE apiAdminReviews.js base path
// together.
@RequestMapping("")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Product review management")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/products/{productId}/reviews")
    @Operation(summary = "List visible reviews for a product",
               description = "Public endpoint. Returns paginated VISIBLE reviews with rating stats.")
    public ResponseEntity<ApiResponse<ProductReviewsResponse>> getProductReviews(
            @PathVariable String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        ProductReviewsResponse result = reviewService.listByProduct(productId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/products/{productId}/reviews")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Create a review for a product",
               description = "Requires a DELIVERED order containing the product. " +
                       "One review per user per product per order.")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String productId,
            @Valid @RequestBody CreateReviewRequest request) {
        String userId = userDetails.getUsername();
        ReviewResponse review = reviewService.create(
                userId, productId,
                request.getOrderId(),
                request.getRating(),
                request.getComment());
        return ResponseEntity.ok(ApiResponse.success("Review submitted successfully", review));
    }

    @PatchMapping("/reviews/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT')")
    @Operation(summary = "Update review status (hide/show)",
               description = "ADMIN/CONTENT only. Changes review visibility and recalculates product rating.")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReviewStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateReviewStatusRequest request) {
        ReviewResponse review = reviewService.updateStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Review status updated", review));
    }

    @GetMapping("/admin/reviews")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT')")
    @Operation(summary = "System-wide review list (admin moderation)",
               description = "Returns enriched rows: productName, userFullName, orderNumber. " +
                       "Optional filters: rating (1-5), status (PUBLISHED|HIDDEN), productId.")
    public ResponseEntity<ApiResponse<PageResponse<AdminReviewView>>> listForAdmin(
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        Page<AdminReviewView> result = reviewService.listForAdmin(rating, status, productId, pageable);
        PageResponse<AdminReviewView> body = PageResponse.<AdminReviewView>builder()
                .items(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
