package com.woodfurni.order.service;

import com.woodfurni.auth.model.Address;
import com.woodfurni.auth.model.User;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.cart.model.Cart;
import com.woodfurni.cart.model.CartItem;
import com.woodfurni.cart.repository.CartRepository;
import com.woodfurni.inventory.exception.InsufficientStockException;
import com.woodfurni.inventory.service.InventoryService;
import com.woodfurni.notification.client.NotificationClient;
import com.woodfurni.order.dto.CheckoutRequest;
import com.woodfurni.order.dto.OrderResponse;
import com.woodfurni.order.dto.PaymentResponse;
import com.woodfurni.order.enums.OrderStatus;
import com.woodfurni.order.enums.PaymentMethod;
import com.woodfurni.order.enums.PaymentStatus;
import com.woodfurni.order.model.Order;
import com.woodfurni.order.repository.OrderRepository;
import com.woodfurni.promotion.dto.ValidatePromotionResponse;
import com.woodfurni.promotion.service.PromotionService;
import com.woodfurni.shipping.dto.ShippingCalculateResponse;
import com.woodfurni.shipping.service.ShippingService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService.checkout()
 *
 * Test scenarios:
 * 1. Checkout thành công — asserts shippingFee snapshot + totalAmount formula
 * 2. Hết hàng giữa chừng → rollback reserve các item trước đó
 * 3. Voucher hết hạn → rollback toàn bộ reservations
 * 4. Cart rỗng → throw ngay lập tức
 * 5. Address không tồn tại → throw
 * 6. Khu vực không hỗ trợ ship → throw rõ ràng, KHÔNG tạo order
 *
 * IMPORTANT: ShippingService.calculateFee() is called OUTSIDE the inventory-reservation
 * try block (see OrderService.checkout()). This means:
 *   - If calculateFee() throws → inventory is NEVER touched → no rollback needed.
 *   - The mock for calculateFee MUST be set up in ALL tests, including the error-path tests.
 *
 * Note: InventoryService is mocked inline inside each test (not as a class field)
 * because Java 23 + ByteBuddy prevents @Mock instrumentation for this service.
 */
class OrderServiceCheckoutTest {

    private static final String USER_ID = "user-123";
    private static final String PRODUCT_A = "prod-A";
    private static final String PRODUCT_B = "prod-B";

    private Cart mockCart;
    private User mockUser;
    private Address mockAddress;
    private CheckoutRequest checkoutRequest;

    // Test-scoped mocks — each test gets fresh instances via new OrderService(...)
    private OrderRepository orderRepository;
    private CartRepository cartRepository;
    private UserRepository userRepository;
    private InventoryService inventoryService;
    private PaymentService paymentService;
    private PromotionService promotionService;
    private NotificationClient notificationClient;
    private ShippingService shippingService;
    private MongoTemplate mongoTemplate;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // Instantiate all service mocks fresh per test to avoid Java 23 ByteBuddy issues
        orderRepository = mock(OrderRepository.class);
        cartRepository = mock(CartRepository.class);
        userRepository = mock(UserRepository.class);
        inventoryService = mock(InventoryService.class);
        paymentService = mock(PaymentService.class);
        promotionService = mock(PromotionService.class);
        notificationClient = mock(NotificationClient.class);
        shippingService = mock(ShippingService.class);
        mongoTemplate = mock(MongoTemplate.class);

        orderService = new OrderService(
                orderRepository, cartRepository, userRepository,
                inventoryService, paymentService, promotionService,
                notificationClient, shippingService, mongoTemplate);

        // Cart with 2 items: 2x 500k + 1x 300k = 1,300,000
        CartItem itemA = CartItem.builder()
                .productId(PRODUCT_A).productName("Bàn gỗ oak")
                .unitPrice(BigDecimal.valueOf(500000)).quantity(2)
                .subtotal(BigDecimal.valueOf(1000000)).build();
        CartItem itemB = CartItem.builder()
                .productId(PRODUCT_B).productName("Ghế bọc da")
                .unitPrice(BigDecimal.valueOf(300000)).quantity(1)
                .subtotal(BigDecimal.valueOf(300000)).build();
        mockCart = Cart.builder()
                .id("cart-1").userId(USER_ID)
                .items(List.of(itemA, itemB))
                .totalAmount(BigDecimal.valueOf(1300000))
                .build();

        // Address: TP.HCM + Quận 1 → within base-radius → fee = 100,000
        mockAddress = Address.builder()
                .id("addr-1").label("Nhà riêng").line1("123 Đường ABC")
                .ward("Phường 1").district("Quận 1").city("TP.HCM")
                .phone("0909123456").build();
        mockUser = User.builder()
                .id(USER_ID)
                .addresses(new ArrayList<>(List.of(mockAddress)))
                .build();

        checkoutRequest = CheckoutRequest.builder()
                .addressId("addr-1")
                .paymentMethod(PaymentMethod.SANDBOX_CARD)
                .build();

        // ── ALWAYS mock calculateFee OUTSIDE the try block ──
        // This is called BEFORE inventory reservation, so every test needs this mock.
        // All test addresses use city="TP.HCM", district="Quận 1" (within base radius).
        when(shippingService.calculateFee(eq("TP.HCM"), anyString()))
                .thenReturn(ShippingCalculateResponse.builder()
                        .fee(new BigDecimal("100000"))
                        .distanceKm(new BigDecimal("8"))
                        .isOutOfProvince(false)
                        .build());
    }

    // ========================================================================
    // TEST 1a: Checkout thành công — sandbox card, asserts shippingFee + totalAmount
    // ========================================================================
    @Nested
    @DisplayName("Checkout thành công")
    class CheckoutSuccessTests {

        @Test
        @DisplayName("Checkout thành công — shippingFee 112k, totalAmount = subtotal + shipping")
        void checkout_Success_ShippingFee112k_TotalCorrect() {
            // Arrange: Quận 10 (12km) → fee = 100,000 + ceil(2)*6,000 = 112,000
            Address addrQuan10 = Address.builder()
                    .id("addr-q10").label("Nhà").line1("1 Đường XYZ")
                    .district("Quận 10").city("TP.HCM")
                    .phone("0909").build();
            User userQ10 = User.builder().id(USER_ID)
                    .addresses(new ArrayList<>(List.of(addrQuan10))).build();
            CheckoutRequest reqQ10 = CheckoutRequest.builder()
                    .addressId("addr-q10").paymentMethod(PaymentMethod.SANDBOX_CARD).build();

            // Override the default 100k mock for Quận 10
            when(shippingService.calculateFee(eq("TP.HCM"), eq("Quận 10")))
                    .thenReturn(ShippingCalculateResponse.builder()
                            .fee(new BigDecimal("112000"))
                            .distanceKm(new BigDecimal("12"))
                            .isOutOfProvince(false)
                            .build());

            when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(mockCart));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userQ10));
            when(promotionService.validateAndCalculate(anyString(), any(BigDecimal.class)))
                    .thenReturn(ValidatePromotionResponse.invalid("No promotion"));

            PaymentResponse mockPayment = PaymentResponse.builder()
                    .id("pay-1").status(PaymentStatus.SUCCESS)
                    .method(PaymentMethod.SANDBOX_CARD)
                    .transactionRef("TXN-ABC123").build();
            when(paymentService.createPayment(anyString(), any(PaymentMethod.class), any(BigDecimal.class)))
                    .thenReturn(mockPayment);
            when(paymentService.getByOrderId(anyString())).thenReturn(mockPayment);

            Order savedOrder = Order.builder()
                    .id("order-1").orderNumber("ORD-20260819-0001").customerId(USER_ID)
                    .status(OrderStatus.PENDING).paymentStatus(PaymentStatus.PAID)
                    .items(new ArrayList<>())
                    .subtotalAmount(BigDecimal.valueOf(1300000))
                    .totalAmount(BigDecimal.valueOf(1412000))  // 1,300,000 + 112,000
                    .discountAmount(BigDecimal.ZERO)
                    .shippingFee(new BigDecimal("112000"))
                    .statusHistory(new ArrayList<>())
                    .build();
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            // Act
            OrderResponse result = orderService.checkout(USER_ID, reqQ10);

            // Assert
            assertNotNull(result);
            assertEquals("order-1", result.getId());
            assertEquals(OrderStatus.PENDING, result.getStatus());
            assertEquals(PaymentStatus.SUCCESS, result.getPaymentStatus());

            assertEquals(new BigDecimal("112000"), result.getShippingFee());
            assertEquals(new BigDecimal("1412000"), result.getTotalAmount());

            // ShippingService called with correct address
            verify(shippingService).calculateFee(eq("TP.HCM"), eq("Quận 10"));

            // Payment created with totalAmount INCLUDING shipping
            verify(paymentService).createPayment(anyString(), eq(PaymentMethod.SANDBOX_CARD),
                    eq(new BigDecimal("1412000")));
        }

        @Test
        @DisplayName("Checkout COD thành công — shippingFee 100k, totalAmount = subtotal + shipping")
        void checkout_Success_COD_ShippingFee100k_TotalCorrect() {
            CheckoutRequest codRequest = CheckoutRequest.builder()
                    .addressId("addr-1").paymentMethod(PaymentMethod.COD).build();

            when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(mockCart));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));

            // COD: payment is PENDING → order.paymentStatus becomes UNPAID (OrderService logic)
            PaymentResponse mockPayment = PaymentResponse.builder()
                    .id("pay-2").status(PaymentStatus.PENDING)
                    .method(PaymentMethod.COD).build();
            when(paymentService.createPayment(anyString(), any(PaymentMethod.class), any(BigDecimal.class)))
                    .thenReturn(mockPayment);
            when(paymentService.getByOrderId(anyString())).thenReturn(mockPayment);

            Order savedOrder = Order.builder()
                    .id("order-2").orderNumber("ORD-20260819-0002").customerId(USER_ID)
                    .status(OrderStatus.PENDING).paymentStatus(PaymentStatus.UNPAID)
                    .items(new ArrayList<>())
                    .subtotalAmount(BigDecimal.valueOf(1300000))
                    .totalAmount(BigDecimal.valueOf(1400000))  // 1,300,000 + 100,000
                    .discountAmount(BigDecimal.ZERO)
                    .shippingFee(new BigDecimal("100000"))
                    .statusHistory(new ArrayList<>())
                    .build();
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            OrderResponse result = orderService.checkout(USER_ID, codRequest);

            assertEquals(new BigDecimal("100000"), result.getShippingFee());
            assertEquals(new BigDecimal("1400000"), result.getTotalAmount());
            assertNotNull(result.getPaymentStatus(), "paymentStatus must be set");
        }
    }

    // ========================================================================
    // TEST 2: Hết hàng giữa chừng → rollback reservations
    // ========================================================================
    @Nested
    @DisplayName("Hết hàng — rollback inventory")
    class OutOfStockTests {

        @Test
        @DisplayName("Hết hàng giữa chừng — rollback reservations vừa reserve, không tạo order")
        void checkout_OutOfStockMidway_RollsBackReservations() {
            // PRODUCT_A reserved OK; PRODUCT_B throws InsufficientStockException
            doNothing().when(inventoryService).initStockIfAbsent(anyString());
            doNothing().when(inventoryService).reserve(eq(PRODUCT_A), eq(2));
            doThrow(new InsufficientStockException(PRODUCT_B, 1, 0))
                    .when(inventoryService).reserve(eq(PRODUCT_B), eq(1));

            when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(mockCart));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.checkout(USER_ID, checkoutRequest));

            assertTrue(ex.getMessage().contains("Insufficient stock"));
            // PRODUCT_A was fully reserved before PRODUCT_B failed → rollback PRODUCT_A only
            verify(inventoryService, times(1)).release(eq(PRODUCT_A), eq(2));
            verify(inventoryService, never()).release(eq(PRODUCT_B), anyInt());
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("Hết hàng ngay item đầu — không cần rollback")
        void checkout_OutOfStockFirstItem_NoRollbackNeeded() {
            doThrow(new InsufficientStockException(PRODUCT_A, 2, 1))
                    .when(inventoryService).reserve(eq(PRODUCT_A), eq(2));

            when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(mockCart));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.checkout(USER_ID, checkoutRequest));

            assertTrue(ex.getMessage().contains("Insufficient stock"));
            verify(inventoryService, never()).release(anyString(), anyInt());
            verify(orderRepository, never()).save(any(Order.class));
        }
    }

    // ========================================================================
    // TEST 3: Voucher hết hạn → rollback reservations
    // ========================================================================
    @Nested
    @DisplayName("Voucher không hợp lệ — rollback inventory")
    class PromotionTests {

        @Test
        @DisplayName("Voucher hết hạn — rollback tất cả reservations rồi throw")
        void checkout_ExpiredPromotion_RollsBackAllAndThrows() {
            doNothing().when(inventoryService).reserve(anyString(), anyInt());

            when(promotionService.validateAndCalculate(anyString(), any(BigDecimal.class)))
                    .thenReturn(ValidatePromotionResponse.invalid("Mã khuyến mãi đã hết hạn"));

            when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(mockCart));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));

            CheckoutRequest reqWithPromo = CheckoutRequest.builder()
                    .addressId("addr-1").paymentMethod(PaymentMethod.SANDBOX_CARD)
                    .promotionCode("EXPIRED2025").build();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.checkout(USER_ID, reqWithPromo));

            assertTrue(ex.getMessage().contains("Invalid promotion"));
            assertTrue(ex.getMessage().contains("hết hạn"));
            verify(inventoryService).release(eq(PRODUCT_A), eq(2));
            verify(inventoryService).release(eq(PRODUCT_B), eq(1));
            verify(orderRepository, never()).save(any(Order.class));
        }
    }

    // ========================================================================
    // TEST 4: Cart rỗng → throw ngay (calculateFee never called)
    // ========================================================================
    @Nested
    @DisplayName("Cart không hợp lệ — không gọi shipping/inventory")
    class CartValidationTests {

        @Test
        @DisplayName("Cart rỗng — throw ngay, không gọi calculateFee hay inventory")
        void checkout_EmptyCart_ThrowsImmediately() {
            Cart emptyCart = Cart.builder()
                    .id("cart-empty").userId(USER_ID)
                    .items(new ArrayList<>())
                    .totalAmount(BigDecimal.ZERO)
                    .build();
            when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(emptyCart));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.checkout(USER_ID, checkoutRequest));

            assertEquals("Cart is empty. Add items before checkout.", ex.getMessage());
            // calculateFee is NEVER called for empty cart (throws before shipping check)
            verify(shippingService, never()).calculateFee(anyString(), anyString());
            verify(inventoryService, never()).reserve(anyString(), anyInt());
        }
    }

    // ========================================================================
    // TEST 5: Address không tồn tại → throw (calculateFee never called)
    // ========================================================================
    @Nested
    @DisplayName("Address không hợp lệ — không gọi shipping/inventory")
    class AddressValidationTests {

        @Test
        @DisplayName("Address không tồn tại — throw, không gọi calculateFee hay inventory")
        void checkout_AddressNotFound_ThrowsWithoutShippingOrInventoryOps() {
            when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(mockCart));
            User noAddressUser = User.builder().id(USER_ID)
                    .addresses(new ArrayList<>()).build();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(noAddressUser));

            Exception ex = assertThrows(Exception.class,
                    () -> orderService.checkout(USER_ID, checkoutRequest));

            assertTrue(ex.getMessage().contains("not found"));
            // calculateFee is NEVER called when address is not found
            verify(shippingService, never()).calculateFee(anyString(), anyString());
            verify(inventoryService, never()).reserve(anyString(), anyInt());
        }
    }

    // ========================================================================
    // TEST 6: Khu vực không hỗ trợ ship → KHÔNG tạo order
    // ========================================================================
    @Nested
    @DisplayName("Khu vực không hỗ trợ ship")
    class UnsupportedAreaTests {

        @Test
        @DisplayName("Khu vực không có trong bảng (HCM) — throw rõ ràng, KHÔNG reserve inventory, KHÔNG tạo order")
        void checkout_UnsupportedArea_ThrowsWithoutCreatingOrder() {
            Address unsupportedAddr = Address.builder()
                    .id("addr-unknown").label("Tỉnh")
                    .district("Quận 99").city("TP.HCM")
                    .phone("0909").build();
            User userUnsupported = User.builder().id(USER_ID)
                    .addresses(new ArrayList<>(List.of(unsupportedAddr))).build();
            CheckoutRequest reqUnsupported = CheckoutRequest.builder()
                    .addressId("addr-unknown").paymentMethod(PaymentMethod.COD).build();

            // calculateFee throws for unsupported HCM district
            when(shippingService.calculateFee(eq("TP.HCM"), eq("Quận 99")))
                    .thenThrow(new IllegalArgumentException("Khu vực chưa được hỗ trợ tính phí ship"));

            when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(mockCart));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userUnsupported));

            // Act & Assert
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.checkout(USER_ID, reqUnsupported));

            // Error message returned directly to customer
            assertEquals("Khu vực chưa được hỗ trợ tính phí ship", ex.getMessage());

            // CRITICAL: inventory was NEVER touched (calculateFee is outside the try block)
            verify(inventoryService, never()).initStockIfAbsent(anyString());
            verify(inventoryService, never()).reserve(anyString(), anyInt());
            verify(inventoryService, never()).release(anyString(), anyInt());

            // No order was saved
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("Tỉnh khác (ngoại tỉnh) — dùng fallback 60km → checkout thành công, fee = 400,000")
        void checkout_OutOfProvince_UsesFallback60km_Success() {
            Address provinceAddr = Address.builder()
                    .id("addr-hanoi").label("Hà Nội")
                    .district("Quận Ba Đình").city("Hà Nội")
                    .phone("0909").build();
            User userHanoi = User.builder().id(USER_ID)
                    .addresses(new ArrayList<>(List.of(provinceAddr))).build();
            CheckoutRequest reqHanoi = CheckoutRequest.builder()
                    .addressId("addr-hanoi").paymentMethod(PaymentMethod.COD).build();

            // calculateFee returns fallback fee for non-HCM city
            when(shippingService.calculateFee(eq("Hà Nội"), eq("Quận Ba Đình")))
                    .thenReturn(ShippingCalculateResponse.builder()
                            .fee(new BigDecimal("400000"))
                            .distanceKm(new BigDecimal("60"))
                            .isOutOfProvince(true)
                            .build());

            when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(mockCart));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userHanoi));
            when(promotionService.validateAndCalculate(anyString(), any(BigDecimal.class)))
                    .thenReturn(ValidatePromotionResponse.invalid("No promotion"));

            // COD: payment is PENDING → order.paymentStatus becomes UNPAID (OrderService logic)
            PaymentResponse mockPayment = PaymentResponse.builder()
                    .id("pay-hn").status(PaymentStatus.PENDING)
                    .method(PaymentMethod.COD).build();
            when(paymentService.createPayment(anyString(), any(PaymentMethod.class), any(BigDecimal.class)))
                    .thenReturn(mockPayment);
            when(paymentService.getByOrderId(anyString())).thenReturn(mockPayment);

            Order savedOrder = Order.builder()
                    .id("order-hn").orderNumber("ORD-20260824-0001").customerId(USER_ID)
                    .status(OrderStatus.PENDING).paymentStatus(PaymentStatus.UNPAID)
                    .items(new ArrayList<>())
                    .subtotalAmount(BigDecimal.valueOf(1300000))
                    .totalAmount(BigDecimal.valueOf(1700000))  // 1,300,000 + 400,000
                    .discountAmount(BigDecimal.ZERO)
                    .shippingFee(new BigDecimal("400000"))
                    .statusHistory(new ArrayList<>())
                    .build();
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            OrderResponse result = orderService.checkout(USER_ID, reqHanoi);

            assertEquals(new BigDecimal("400000"), result.getShippingFee());
            assertEquals(new BigDecimal("1700000"), result.getTotalAmount());
            assertNotNull(result.getPaymentStatus(), "paymentStatus must be set");
            verify(orderRepository).save(any(Order.class));
        }
    }
}
