package com.woodfurni.order.service;

import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.cart.repository.CartRepository;
import com.woodfurni.inventory.service.InventoryService;
import com.woodfurni.notification.client.NotificationClient;
import com.woodfurni.order.dto.OrderResponse;
import com.woodfurni.order.dto.TrackingUpdateRequest;
import com.woodfurni.order.enums.OrderStatus;
import com.woodfurni.order.enums.PaymentStatus;
import com.woodfurni.order.model.Order;
import com.woodfurni.order.model.OrderItem;
import com.woodfurni.order.model.ShippingAddress;
import com.woodfurni.order.model.TrackingUpdate;
import com.woodfurni.order.repository.OrderRepository;
import com.woodfurni.promotion.service.PromotionService;
import com.woodfurni.shipping.service.ShippingService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderService#addTrackingUpdate(String, TrackingUpdateRequest, String)}.
 *
 * Scenarios covered:
 * 1. Two tracking updates with isDelivered=false on a SHIPPING order →
 *    status stays SHIPPING, trackingUpdates has 2 entries, realtime notify fires twice.
 * 2. Third call with isDelivered=true → status flips to DELIVERED, inventory
 *    committed for every item, statusHistory gains DELIVERED entry, notify fires.
 * 3. Tracking update on a non-SHIPPING order (PENDING) → IllegalArgumentException,
 *    no state change, no inventory touch, no notify.
 * 4. getTrackingUpdatesForUser: staff can read any order's timeline,
 *    customer only their own; otherwise rejected.
 */
class OrderServiceTrackingUpdateTest {

    private static final String ORDER_ID = "order-1";
    private static final String CUSTOMER_ID = "cust-1";
    private static final String WAREHOUSE_USER = "warehouse-staff-1";
    private static final String PRODUCT_A = "prod-A";
    private static final String PRODUCT_B = "prod-B";

    private OrderRepository orderRepository;
    private CartRepository cartRepository;
    private UserRepository userRepository;
    private InventoryService inventoryService;
    private PaymentService paymentService;
    private PromotionService promotionService;
    private NotificationClient notificationClient;
    private ShippingService shippingService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        cartRepository = mock(CartRepository.class);
        userRepository = mock(UserRepository.class);
        inventoryService = mock(InventoryService.class);
        paymentService = mock(com.woodfurni.order.service.PaymentService.class);
        promotionService = mock(PromotionService.class);
        notificationClient = mock(NotificationClient.class);
        shippingService = mock(ShippingService.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);

        orderService = new OrderService(
                orderRepository, cartRepository, userRepository,
                inventoryService, paymentService, promotionService,
                notificationClient, shippingService, mongoTemplate);

        // Order saved → return what was passed (so chained reads see the
        // mutations we just made: tracking updates + status flip).
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    private Order buildShippingOrder(int qtyA, int qtyB) {
        List<OrderItem> items = new ArrayList<>();
        items.add(OrderItem.builder()
                .productId(PRODUCT_A).productName("Bàn gỗ oak")
                .unitPrice(BigDecimal.valueOf(500000)).quantity(qtyA)
                .subtotal(BigDecimal.valueOf(500000L * qtyA))
                .build());
        items.add(OrderItem.builder()
                .productId(PRODUCT_B).productName("Ghế bọc da")
                .unitPrice(BigDecimal.valueOf(300000)).quantity(qtyB)
                .subtotal(BigDecimal.valueOf(300000L * qtyB))
                .build());

        Order order = Order.builder()
                .id(ORDER_ID)
                .orderNumber("ORD-20260822-0001")
                .customerId(CUSTOMER_ID)
                .items(items)
                .subtotalAmount(BigDecimal.valueOf(500000L * qtyA + 300000L * qtyB))
                .shippingFee(BigDecimal.valueOf(30000))
                .totalAmount(BigDecimal.valueOf(500000L * qtyA + 300000L * qtyB + 30000))
                .shippingAddress(ShippingAddress.builder()
                        .label("Nhà riêng").line1("123 ABC").city("TP.HCM")
                        .phone("0909123456").build())
                .paymentStatus(PaymentStatus.PAID)
                .status(OrderStatus.SHIPPING)
                .statusHistory(new ArrayList<>())
                .trackingUpdates(new ArrayList<>())
                .build();
        return order;
    }

    private TrackingUpdateRequest req(String status, String location, Boolean isDelivered) {
        return TrackingUpdateRequest.builder()
                .status(status)
                .location(location)
                .note("auto-test")
                .isDelivered(isDelivered)
                .build();
    }

    // ====================================================================
    // TEST 1: two intermediate tracking updates → status stays SHIPPING
    // ====================================================================

    @Test
    @DisplayName("addTrackingUpdate x2 với isDelivered=false → status vẫn SHIPPING, 2 tracking entries, notify 2 lần")
    void addTrackingUpdate_intermediateUpdates_keepShippingStatus() {
        Order order = buildShippingOrder(2, 1);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // --- Lần 1: "Đã lấy hàng" ---
        OrderResponse r1 = orderService.addTrackingUpdate(
                ORDER_ID, req("Đã lấy hàng", "Kho HN", false), WAREHOUSE_USER);

        // --- Lần 2: "Đang vận chuyển" ---
        OrderResponse r2 = orderService.addTrackingUpdate(
                ORDER_ID, req("Đang vận chuyển", "Quận 1, HCM", false), WAREHOUSE_USER);

        // Status KHÔNG đổi
        assertEquals(OrderStatus.SHIPPING, order.getStatus(),
                "Status must remain SHIPPING when isDelivered=false");
        assertEquals(OrderStatus.SHIPPING, r1.getStatus());
        assertEquals(OrderStatus.SHIPPING, r2.getStatus());

        // trackingUpdates phải có đúng 2 entry, lưu đúng thứ tự
        assertEquals(2, order.getTrackingUpdates().size());
        assertEquals("Đã lấy hàng", order.getTrackingUpdates().get(0).getStatus());
        assertEquals("Kho HN", order.getTrackingUpdates().get(0).getLocation());
        assertEquals(WAREHOUSE_USER, order.getTrackingUpdates().get(0).getUpdatedBy());
        assertEquals("Đang vận chuyển", order.getTrackingUpdates().get(1).getStatus());

        // statusHistory KHÔNG thêm entry (vì status không đổi)
        assertEquals(0, order.getStatusHistory().size(),
                "No statusHistory entries should be added when status doesn't change");

        // KHÔNG gọi inventory commit
        verify(inventoryService, never()).commit(anyString(), anyInt());

        // Notify realtime cho customer — 2 lần
        verify(notificationClient, times(2)).notifyOrderStatus(
                eq(ORDER_ID), anyString(), eq("SHIPPING"), eq(CUSTOMER_ID));
    }

    // ====================================================================
    // TEST 2: third call with isDelivered=true → flip to DELIVERED + commit
    // ====================================================================

    @Test
    @DisplayName("addTrackingUpdate với isDelivered=true → status DELIVERED, inventory.commit cho mỗi item, statusHistory +1, notify DELIVERED")
    void addTrackingUpdate_isDeliveredTrue_transitionsToDeliveredAndCommitsInventory() {
        Order order = buildShippingOrder(2, 1);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // Pre-load 2 intermediate tracking entries to mimic real flow
        orderService.addTrackingUpdate(
                ORDER_ID, req("Đã lấy hàng", "Kho HN", false), WAREHOUSE_USER);
        orderService.addTrackingUpdate(
                ORDER_ID, req("Đang vận chuyển", "Quận 1, HCM", false), WAREHOUSE_USER);

        // --- Lần 3: giao thành công ---
        OrderResponse delivered = orderService.addTrackingUpdate(
                ORDER_ID, req("Đã giao hàng", "123 Nguyễn Huệ", true), WAREHOUSE_USER);

        // Status flips to DELIVERED
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertEquals(OrderStatus.DELIVERED, delivered.getStatus());

        // Tracking entry cuối cùng vẫn được ghi (entry về "Đã giao hàng")
        assertEquals(3, order.getTrackingUpdates().size());
        TrackingUpdate last = order.getTrackingUpdates().get(2);
        assertEquals("Đã giao hàng", last.getStatus());
        assertEquals("123 Nguyễn Huệ", last.getLocation());
        assertEquals(WAREHOUSE_USER, last.getUpdatedBy());

        // statusHistory +1 cho DELIVERED
        assertEquals(1, order.getStatusHistory().size(),
                "Exactly one statusHistory entry expected for the DELIVERED flip");
        assertEquals("DELIVERED", order.getStatusHistory().get(0).getStatus());
        assertEquals(WAREHOUSE_USER, order.getStatusHistory().get(0).getChangedBy());

        // Inventory committed cho CẢ 2 items, đúng qty
        ArgumentCaptor<String> productIds = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> qtys = ArgumentCaptor.forClass(Integer.class);
        verify(inventoryService, times(2)).commit(productIds.capture(), qtys.capture());
        assertTrue(productIds.getAllValues().contains(PRODUCT_A));
        assertTrue(productIds.getAllValues().contains(PRODUCT_B));
        assertEquals(3, qtys.getAllValues().stream().mapToInt(Integer::intValue).sum(),
                "Total committed quantity should match ordered qty (A=2, B=1 → 3)");

        // Notify đủ 3 lần: 2 SHIPPING (intermediate) + 1 DELIVERED (cuối)
        ArgumentCaptor<String> statuses = ArgumentCaptor.forClass(String.class);
        verify(notificationClient, times(3)).notifyOrderStatus(
                eq(ORDER_ID), anyString(), statuses.capture(), eq(CUSTOMER_ID));
        List<String> sent = statuses.getAllValues();
        assertEquals(List.of("SHIPPING", "SHIPPING", "DELIVERED"), sent);
    }

    // ====================================================================
    // TEST 3: tracking-update on non-SHIPPING order → rejected
    // ====================================================================

    @Test
    @DisplayName("addTrackingUpdate trên đơn KHÔNG ở SHIPPING (vd PENDING) → IllegalArgumentException")
    void addTrackingUpdate_nonShippingOrder_throwsAndDoesNotMutate() {
        Order order = buildShippingOrder(1, 1);
        order.setStatus(OrderStatus.PENDING); // chưa ship
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                orderService.addTrackingUpdate(
                        ORDER_ID, req("Đã lấy hàng", "Kho HN", false), WAREHOUSE_USER));

        assertTrue(ex.getMessage().contains("Chỉ cập nhật tracking khi đơn đang giao"),
                "Error message must explicitly say tracking only allowed while shipping: " + ex.getMessage());

        // Không có thay đổi nào
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(0, order.getTrackingUpdates().size());
        assertEquals(0, order.getStatusHistory().size());

        verify(inventoryService, never()).commit(anyString(), anyInt());
        verify(notificationClient, never()).notifyOrderStatus(
                anyString(), anyString(), anyString(), anyString());
        verify(orderRepository, never()).save(any(Order.class));
    }

    // ====================================================================
    // TEST 4: same protection applies to PROCESSING / CONFIRMED / DELIVERED
    // ====================================================================

    @Test
    @DisplayName("addTrackingUpdate bị reject cho mọi non-SHIPPING status (PROCESSING/CONFIRMED/DELIVERED)")
    void addTrackingUpdate_rejectsAllNonShippingStatuses() {
        for (OrderStatus nonShipping : List.of(
                OrderStatus.PENDING, OrderStatus.CONFIRMED,
                OrderStatus.PROCESSING, OrderStatus.DELIVERED,
                OrderStatus.CANCELLED, OrderStatus.RETURNED)) {
            Order order = buildShippingOrder(1, 0);
            order.setStatus(nonShipping);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(IllegalArgumentException.class, () ->
                    orderService.addTrackingUpdate(
                            ORDER_ID, req("Đang giao", "Q1", false), WAREHOUSE_USER),
                    "Status " + nonShipping + " should reject tracking update");
        }
    }

    // ====================================================================
    // TEST 5: getTrackingUpdatesForUser — staff allowed, owner allowed, others denied
    // ====================================================================

    @Test
    @DisplayName("getTrackingUpdatesForUser: staff luôn được, customer chỉ đơn của mình, người khác bị từ chối")
    void getTrackingUpdatesForUser_ownerAndStaffAllowed_otherCustomersDenied() {
        Order order = buildShippingOrder(1, 0);
        order.getTrackingUpdates().add(TrackingUpdate.builder()
                .status("Đã lấy hàng").location("Kho HN")
                .updatedBy(WAREHOUSE_USER).build());
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // Staff → OK
        List<TrackingUpdate> staffView = orderService.getTrackingUpdatesForUser(
                ORDER_ID, "any-staff-id", /*isStaff=*/ true);
        assertEquals(1, staffView.size());

        // Owner customer → OK
        List<TrackingUpdate> ownerView = orderService.getTrackingUpdatesForUser(
                ORDER_ID, CUSTOMER_ID, /*isStaff=*/ false);
        assertEquals(1, ownerView.size());

        // Another customer → reject
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                orderService.getTrackingUpdatesForUser(
                        ORDER_ID, "another-customer-id", /*isStaff=*/ false));
        assertTrue(ex.getMessage().toLowerCase().contains("quyền"));
    }
}
