package com.woodfurni.review.service;

import com.woodfurni.catalog.product.model.Product;
import com.woodfurni.catalog.product.repository.ProductRepository;
import com.woodfurni.order.enums.OrderStatus;
import com.woodfurni.order.model.Order;
import com.woodfurni.order.model.OrderItem;
import com.woodfurni.order.repository.OrderRepository;
import com.woodfurni.review.dto.ReviewResponse;
import com.woodfurni.review.enums.ReviewStatus;
import com.woodfurni.review.exception.DuplicateReviewException;
import com.woodfurni.review.exception.OrderNotFoundException;
import com.woodfurni.review.exception.OrderOwnershipException;
import com.woodfurni.review.model.Review;
import com.woodfurni.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReviewService.create()
 *
 * Validation chain tests (CHECK 1-5):
 *
 * TEST 1: Non-existing orderId
 *         → OrderNotFoundException → 404
 *
 * TEST 2: Existing order belonging to another customer
 *         → OrderOwnershipException → 403
 *
 * TEST 3: Order belongs to current customer but status = CONFIRMED
 *         → IllegalArgumentException → 400
 *
 * TEST 4: DELIVERED order but productId is not inside order.items
 *         → IllegalArgumentException → 400
 *
 * TEST 5: DELIVERED order contains product and an existing review already exists
 *         → DuplicateReviewException → 409
 *
 * TEST 6: DELIVERED order contains product and no duplicate review exists
 *         → Review created successfully (201)
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ReviewService reviewService;

    private static final String USER_ID = "user-123";
    private static final String OTHER_USER_ID = "user-other";
    private static final String PRODUCT_ID = "prod-001";
    private static final String ORDER_ID = "order-001";

    private OrderItem makeOrderItem(String productId) {
        return OrderItem.builder()
                .productId(productId)
                .productName("Test Product")
                .unitPrice(BigDecimal.valueOf(100))
                .quantity(1)
                .subtotal(BigDecimal.valueOf(100))
                .build();
    }

    private Order makeOrder(String orderId, String customerId, OrderStatus status, List<OrderItem> items) {
        return Order.builder()
                .id(orderId)
                .orderNumber("ORD-" + orderId)
                .customerId(customerId)
                .status(status)
                .items(items)
                .build();
    }

    // ============================================================
    // TEST 1: Order not found → 404
    // ============================================================
    @Test
    @DisplayName("[CHECK 1] Non-existing orderId → throws OrderNotFoundException (→ 404)")
    void create_NonExistingOrder_ThrowsOrderNotFoundException() {
        // Arrange: order does not exist
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        OrderNotFoundException ex = assertThrows(
                OrderNotFoundException.class,
                () -> reviewService.create(USER_ID, PRODUCT_ID, ORDER_ID, 5, "Great product!")
        );

        assertEquals("Order not found: " + ORDER_ID, ex.getMessage());
        assertEquals(ORDER_ID, ex.getOrderId());

        // Validation stopped at CHECK 1 — no further DB calls
        verify(orderRepository, times(1)).findById(ORDER_ID);
        verify(reviewRepository, never()).existsByUserIdAndProductIdAndOrderId(any(), any(), any());
        verify(reviewRepository, never()).save(any());
    }

    // ============================================================
    // TEST 2: Order belongs to another user → 403
    // ============================================================
    @Test
    @DisplayName("[CHECK 2] Existing order belonging to another customer → throws OrderOwnershipException (→ 403)")
    void create_OrderBelongsToAnotherUser_ThrowsOrderOwnershipException() {
        // Arrange
        Order otherUsersOrder = makeOrder(ORDER_ID, OTHER_USER_ID, OrderStatus.DELIVERED,
                List.of(makeOrderItem(PRODUCT_ID)));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(otherUsersOrder));

        // Act & Assert
        OrderOwnershipException ex = assertThrows(
                OrderOwnershipException.class,
                () -> reviewService.create(USER_ID, PRODUCT_ID, ORDER_ID, 5, "Nice!")
        );

        assertEquals("Order does not belong to this user", ex.getMessage());

        // Validation stopped at CHECK 2 — no further checks
        verify(orderRepository, times(1)).findById(ORDER_ID);
        verify(reviewRepository, never()).existsByUserIdAndProductIdAndOrderId(any(), any(), any());
    }

    // ============================================================
    // TEST 3: Order not DELIVERED → 400
    // ============================================================
    @Test
    @DisplayName("[CHECK 3] Order CONFIRMED (not DELIVERED) → throws IllegalArgumentException (→ 400)")
    void create_OrderNotDelivered_ThrowsIllegalArgumentException() {
        // Arrange: order belongs to user but status = CONFIRMED
        Order pendingOrder = makeOrder(ORDER_ID, USER_ID, OrderStatus.CONFIRMED,
                List.of(makeOrderItem(PRODUCT_ID)));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(pendingOrder));

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> reviewService.create(USER_ID, PRODUCT_ID, ORDER_ID, 5, "Good!")
        );

        assertTrue(ex.getMessage().contains("Only delivered orders can be reviewed"));
        assertTrue(ex.getMessage().contains("CONFIRMED"));

        // Validation stopped at CHECK 3
        verify(orderRepository, times(1)).findById(ORDER_ID);
        verify(reviewRepository, never()).existsByUserIdAndProductIdAndOrderId(any(), any(), any());
    }

    // ============================================================
    // TEST 4: Product not in order items → 400
    // ============================================================
    @Test
    @DisplayName("[CHECK 4] DELIVERED order but productId not in order.items → throws IllegalArgumentException (→ 400)")
    void create_ProductNotInOrder_ThrowsIllegalArgumentException() {
        // Arrange: DELIVERED order but contains different product
        Order deliveredOrder = makeOrder(ORDER_ID, USER_ID, OrderStatus.DELIVERED,
                List.of(makeOrderItem("other-product-999")));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(deliveredOrder));

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> reviewService.create(USER_ID, PRODUCT_ID, ORDER_ID, 5, "Nice!")
        );

        assertTrue(ex.getMessage().contains("This product was not in order"));
        assertTrue(ex.getMessage().contains("ORD-" + ORDER_ID));

        // Validation stopped at CHECK 4
        verify(orderRepository, times(1)).findById(ORDER_ID);
        verify(reviewRepository, never()).existsByUserIdAndProductIdAndOrderId(any(), any(), any());
    }

    // ============================================================
    // TEST 5: Duplicate review → 409
    // ============================================================
    @Test
    @DisplayName("[CHECK 5] Existing review for same user+product+order → throws DuplicateReviewException (→ 409)")
    void create_DuplicateReview_ThrowsDuplicateReviewException() {
        // Arrange: DELIVERED order with the product, but already reviewed
        Order deliveredOrder = makeOrder(ORDER_ID, USER_ID, OrderStatus.DELIVERED,
                List.of(makeOrderItem(PRODUCT_ID)));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(deliveredOrder));
        when(reviewRepository.existsByUserIdAndProductIdAndOrderId(USER_ID, PRODUCT_ID, ORDER_ID))
                .thenReturn(true);

        // Act & Assert
        DuplicateReviewException ex = assertThrows(
                DuplicateReviewException.class,
                () -> reviewService.create(USER_ID, PRODUCT_ID, ORDER_ID, 5, "Amazing!")
        );

        assertEquals("You have already reviewed this product for this order", ex.getMessage());

        // Validation stopped at CHECK 5 — no save
        verify(reviewRepository, never()).save(any());
    }

    // ============================================================
    // TEST 6: Success — all 5 checks pass
    // ============================================================
    @Test
    @DisplayName("[ALL CHECKS PASS] Review created successfully with PUBLISHED status")
    void create_AllChecksPass_ReviewCreatedSuccessfully() {
        // Arrange
        Order deliveredOrder = makeOrder(ORDER_ID, USER_ID, OrderStatus.DELIVERED,
                List.of(makeOrderItem(PRODUCT_ID)));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(deliveredOrder));
        when(reviewRepository.existsByUserIdAndProductIdAndOrderId(USER_ID, PRODUCT_ID, ORDER_ID))
                .thenReturn(false);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        Review savedReview = Review.builder()
                .id("review-1")
                .productId(PRODUCT_ID)
                .userId(USER_ID)
                .orderId(ORDER_ID)
                .rating(5)
                .comment("Amazing product!")
                .status(ReviewStatus.PUBLISHED)
                .build();
        when(reviewRepository.save(any(Review.class))).thenReturn(savedReview);

        // Act
        ReviewResponse response = reviewService.create(USER_ID, PRODUCT_ID, ORDER_ID, 5, "Amazing product!");

        // Assert
        assertNotNull(response);
        assertEquals("review-1", response.getId());
        assertEquals(PRODUCT_ID, response.getProductId());
        assertEquals(USER_ID, response.getUserId());
        assertEquals(ORDER_ID, response.getOrderId());
        assertEquals(5, response.getRating());
        assertEquals("Amazing product!", response.getComment());
        assertEquals(ReviewStatus.PUBLISHED, response.getStatus());

        // Verify review was saved with correct fields
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        Review captured = captor.getValue();
        assertEquals(USER_ID, captured.getUserId());       // from SecurityContext, not request
        assertEquals(PRODUCT_ID, captured.getProductId());
        assertEquals(ORDER_ID, captured.getOrderId());
        assertEquals(5, captured.getRating());
        assertEquals("Amazing product!", captured.getComment());
        assertEquals(ReviewStatus.PUBLISHED, captured.getStatus());
    }

    // ============================================================
    // BONUS: Order DELIVERED but CANCELLED status also rejected
    // ============================================================
    @Test
    @DisplayName("[CHECK 3] Order CANCELLED is also rejected — only DELIVERED allowed")
    void create_OrderCancelled_ThrowsIllegalArgumentException() {
        Order cancelledOrder = makeOrder(ORDER_ID, USER_ID, OrderStatus.CANCELLED,
                List.of(makeOrderItem(PRODUCT_ID)));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(cancelledOrder));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> reviewService.create(USER_ID, PRODUCT_ID, ORDER_ID, 5, "OK")
        );

        assertTrue(ex.getMessage().contains("Only delivered orders can be reviewed"));
        assertTrue(ex.getMessage().contains("CANCELLED"));
    }

    // ============================================================
    // BONUS: rating recalculation called on success
    // ============================================================
    @Test
    @DisplayName("[SIDE EFFECT] On success, recalculateProductRating is called")
    void create_Success_RecalculatesProductRating() {
        // Arrange
        Order deliveredOrder = makeOrder(ORDER_ID, USER_ID, OrderStatus.DELIVERED,
                List.of(makeOrderItem(PRODUCT_ID)));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(deliveredOrder));
        when(reviewRepository.existsByUserIdAndProductIdAndOrderId(USER_ID, PRODUCT_ID, ORDER_ID))
                .thenReturn(false);

        Product product = Product.builder()
                .id(PRODUCT_ID)
                .name("Test Product")
                .ratingCount(0)
                .ratingAverage(0.0)
                .build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(reviewRepository.findByProductIdAndStatus(PRODUCT_ID, ReviewStatus.PUBLISHED))
                .thenReturn(List.of());

        Review savedReview = Review.builder()
                .id("review-1")
                .productId(PRODUCT_ID)
                .userId(USER_ID)
                .orderId(ORDER_ID)
                .rating(4)
                .status(ReviewStatus.PUBLISHED)
                .build();
        when(reviewRepository.save(any(Review.class))).thenReturn(savedReview);

        // Act
        reviewService.create(USER_ID, PRODUCT_ID, ORDER_ID, 4, "Good!");

        // Assert: product rating recalculated
        verify(productRepository).findById(PRODUCT_ID);
        verify(productRepository).save(any(Product.class));
    }
}
