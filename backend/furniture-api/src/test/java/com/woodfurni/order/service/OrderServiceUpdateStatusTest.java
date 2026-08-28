package com.woodfurni.order.service;

import com.woodfurni.cart.repository.CartRepository;
import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.inventory.service.InventoryService;
import com.woodfurni.notification.client.NotificationClient;
import com.woodfurni.order.enums.OrderStatus;
import com.woodfurni.order.enums.PaymentStatus;
import com.woodfurni.order.model.Order;
import com.woodfurni.order.model.OrderItem;
import com.woodfurni.order.dto.OrderResponse;
import com.woodfurni.order.repository.OrderRepository;
import com.woodfurni.promotion.service.PromotionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService — role-based state machine transitions.
 *
 * Role transition matrix:
 * ┌──────────────┬─────────────────────────────────────────────────────────────┐
 * │ Transition   │ Allowed roles                                                 │
 * ├──────────────┼─────────────────────────────────────────────────────────────┤
 * │ PENDING→CONF │ SALES, ADMIN                                                 │
 * │ CONF→PROCESS │ SALES, ADMIN  (also: /send-to-warehouse convenience endpoint) │
 * │ PROCESS→SHIP │ WAREHOUSE, ADMIN (also: /mark-prepared convenience endpoint)  │
 * │ SHIP→DELIVER │ SALES, WAREHOUSE, ADMIN                                       │
 * │ →CANCELLED   │ owner when PENDING/CONF; ADMIN always                         │
 * └──────────────┴─────────────────────────────────────────────────────────────┘
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceUpdateStatusTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private InventoryService inventoryService;
    @Mock private PaymentService paymentService;
    @Mock private PromotionService promotionService;
    @Mock private NotificationClient notificationClient;

    @InjectMocks private OrderService orderService;

    private static final String ORDER_ID = "order-1";
    private static final String ACTOR_ID = "actor-1";

    private Order orderWith(OrderStatus status, List<OrderItem> items) {
        return Order.builder()
                .id(ORDER_ID)
                .orderNumber("ORD-20260819-0001")
                .customerId("customer-1")
                .status(status)
                .paymentStatus(PaymentStatus.PAID)
                .items(items)
                .subtotalAmount(new BigDecimal("1000000"))
                .totalAmount(new BigDecimal("1000000"))
                .discountAmount(BigDecimal.ZERO)
                .statusHistory(new ArrayList<>())
                .build();
    }

    private OrderItem item(String productId, int qty) {
        return OrderItem.builder()
                .productId(productId)
                .productName("Bàn " + productId)
                .sku("SKU-" + productId)
                .unitPrice(new BigDecimal("500000"))
                .quantity(qty)
                .subtotal(new BigDecimal(500000L * qty))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Role-based transition tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PENDING → CONFIRMED")
    class PendingToConfirmed {

        @Test
        @DisplayName("SALES → SUCCESS")
        void sales_can_confirm() {
            Order order = orderWith(OrderStatus.PENDING, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            OrderResponse r = orderService.updateStatus(ORDER_ID, "CONFIRMED", ACTOR_ID, "SALES");

            assertEquals(OrderStatus.CONFIRMED, r.getStatus());
        }

        @Test
        @DisplayName("WAREHOUSE → REJECTED with descriptive message")
        void warehouse_cannot_confirm() {
            Order order = orderWith(OrderStatus.PENDING, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.updateStatus(ORDER_ID, "CONFIRMED", ACTOR_ID, "WAREHOUSE"));

            assertTrue(ex.getMessage().contains("Sales"));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("ADMIN → SUCCESS")
        void admin_can_confirm() {
            Order order = orderWith(OrderStatus.PENDING, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            OrderResponse r = orderService.updateStatus(ORDER_ID, "CONFIRMED", ACTOR_ID, "ADMIN");

            assertEquals(OrderStatus.CONFIRMED, r.getStatus());
        }
    }

    @Nested
    @DisplayName("CONFIRMED → PROCESSING (send-to-warehouse)")
    class ConfirmedToProcessing {

        @Test
        @DisplayName("SALES → SUCCESS via /send-to-warehouse")
        void sales_can_send_to_warehouse() {
            Order order = orderWith(OrderStatus.CONFIRMED, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            OrderResponse r = orderService.sendToWarehouse(ORDER_ID, ACTOR_ID, "SALES");

            assertEquals(OrderStatus.PROCESSING, r.getStatus());
        }

        @Test
        @DisplayName("SALES via PATCH /status → REJECTED (use /send-to-warehouse instead)")
        void sales_via_patch_rejected() {
            Order order = orderWith(OrderStatus.CONFIRMED, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            // Generic updateStatus must NOT allow the CONFIRMED → PROCESSING
            // transition for any role — that transition is owned by the
            // dedicated /send-to-warehouse endpoint so the matching realtime
            // notify + role-checked PreAuthorize runs.
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.updateStatus(ORDER_ID, "PROCESSING", ACTOR_ID, "SALES"));

            assertTrue(ex.getMessage().contains("Invalid status transition"),
                    "Generic endpoint must reject CONFIRMED → PROCESSING; got: " + ex.getMessage());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("WAREHOUSE calling send-to-warehouse → REJECTED with descriptive message")
        void warehouse_cannot_send_to_warehouse() {
            Order order = orderWith(OrderStatus.CONFIRMED, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.sendToWarehouse(ORDER_ID, ACTOR_ID, "WAREHOUSE"));

            assertTrue(ex.getMessage().contains("Sales"));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("WAREHOUSE via PATCH /status → REJECTED")
        void warehouse_via_patch_rejected() {
            Order order = orderWith(OrderStatus.CONFIRMED, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.updateStatus(ORDER_ID, "PROCESSING", ACTOR_ID, "WAREHOUSE"));

            assertTrue(ex.getMessage().contains("Sales"),
                    "Error message must direct non-Sales to use /send-to-warehouse; got: " + ex.getMessage());
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("PROCESSING → SHIPPING (mark-prepared)")
    class ProcessingToShipping {

        @Test
        @DisplayName("WAREHOUSE → SUCCESS")
        void warehouse_can_mark_prepared() {
            Order order = orderWith(OrderStatus.PROCESSING, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            OrderResponse r = orderService.markPrepared(ORDER_ID, ACTOR_ID, "WAREHOUSE");

            assertEquals(OrderStatus.SHIPPING, r.getStatus());
        }

        @Test
        @DisplayName("SALES calling mark-prepared → REJECTED with descriptive message")
        void sales_cannot_mark_prepared() {
            Order order = orderWith(OrderStatus.PROCESSING, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.markPrepared(ORDER_ID, ACTOR_ID, "SALES"));

            assertTrue(ex.getMessage().contains("Warehouse"));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("WAREHOUSE via PATCH /status → REJECTED (use mark-prepared instead)")
        void warehouse_via_patch_rejected() {
            Order order = orderWith(OrderStatus.PROCESSING, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            // Generic updateStatus must NOT allow WAREHOUSE to flip PROCESSING → SHIPPING.
            // They have to go through the dedicated /mark-prepared endpoint.
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.updateStatus(ORDER_ID, "SHIPPING", ACTOR_ID, "WAREHOUSE"));

            assertTrue(ex.getMessage().contains("Invalid status transition"));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("SALES via PATCH /status → REJECTED (must wait for Warehouse mark-prepared)")
        void sales_via_patch_rejected_processing_to_shipping() {
            // BUSINESS RULE: after Sales "gửi qua Warehouse" (CONFIRMED → PROCESSING),
            // Sales must NOT be able to flip the order to SHIPPING themselves.
            // They have to wait for Warehouse Staff to confirm "đã chuẩn bị xong"
            // before Sales can resume shipping-related actions (tracking / delivered).
            Order order = orderWith(OrderStatus.PROCESSING, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.updateStatus(ORDER_ID, "SHIPPING", ACTOR_ID, "SALES"));

            assertTrue(ex.getMessage().contains("Warehouse"),
                    "Error message must direct Sales to wait for Warehouse; got: " + ex.getMessage());
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("SHIPPING → DELIVERED")
    class ShippingToDelivered {

        @Test
        @DisplayName("SALES → SUCCESS")
        void sales_can_mark_delivered() {
            Order order = orderWith(OrderStatus.SHIPPING, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            OrderResponse r = orderService.updateStatus(ORDER_ID, "DELIVERED", ACTOR_ID, "SALES");

            assertEquals(OrderStatus.DELIVERED, r.getStatus());
        }

        @Test
        @DisplayName("WAREHOUSE → SUCCESS")
        void warehouse_can_mark_delivered() {
            Order order = orderWith(OrderStatus.SHIPPING, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            OrderResponse r = orderService.updateStatus(ORDER_ID, "DELIVERED", ACTOR_ID, "WAREHOUSE");

            assertEquals(OrderStatus.DELIVERED, r.getStatus());
        }

        @Test
        @DisplayName("CONTENT → REJECTED")
        void content_cannot_mark_delivered() {
            Order order = orderWith(OrderStatus.SHIPPING, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.updateStatus(ORDER_ID, "DELIVERED", ACTOR_ID, "CONTENT"));

            assertTrue(ex.getMessage().contains("Sales") || ex.getMessage().contains("Warehouse"),
                    "Error message must name the roles that own SHIPPING → DELIVERED; got: " + ex.getMessage());
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("ADMIN bypass")
    class AdminBypass {

        @ParameterizedTest
        @CsvSource({
                "PENDING, CONFIRMED",
                "CONFIRMED, PROCESSING",
                "PROCESSING, SHIPPING",
                "SHIPPING, DELIVERED",
        })
        @DisplayName("ADMIN can perform any valid transition")
        void admin_can_do_any_valid_transition(OrderStatus from, OrderStatus to) {
            List<OrderItem> items = List.of(item("p1", 1));
            Order order = orderWith(from, items);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            OrderResponse r = orderService.updateStatus(ORDER_ID, to.name(), ACTOR_ID, "ADMIN");

            assertEquals(to, r.getStatus());
        }
    }

    @Nested
    @DisplayName("Inventory side-effects")
    class InventorySideEffects {

        @Test
        @DisplayName("CONFIRMED → CANCELLED — releases inventory")
        void cancelled_releases_inventory() {
            List<OrderItem> items = List.of(item("prod-A", 2), item("prod-B", 1));
            Order order = orderWith(OrderStatus.CONFIRMED, items);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            OrderResponse r = orderService.updateStatus(ORDER_ID, "CANCELLED", ACTOR_ID, "ADMIN");

            assertEquals(OrderStatus.CANCELLED, r.getStatus());
            assertEquals(PaymentStatus.REFUNDED, r.getPaymentStatus());
            verify(inventoryService).release(eq("prod-A"), eq(2));
            verify(inventoryService).release(eq("prod-B"), eq(1));
            verify(inventoryService, never()).commit(anyString(), anyInt());
        }

        @Test
        @DisplayName("SHIPPING → DELIVERED — commits inventory")
        void delivered_commits_inventory() {
            List<OrderItem> items = List.of(item("prod-A", 2));
            Order order = orderWith(OrderStatus.SHIPPING, items);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            OrderResponse r = orderService.updateStatus(ORDER_ID, "DELIVERED", ACTOR_ID, "SALES");

            assertEquals(OrderStatus.DELIVERED, r.getStatus());
            verify(inventoryService).commit(eq("prod-A"), eq(2));
            verify(inventoryService, never()).release(anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("PENDING → SHIPPING (skip steps) — rejected")
        void skip_steps_rejected() {
            Order order = orderWith(OrderStatus.PENDING, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(IllegalArgumentException.class,
                    () -> orderService.updateStatus(ORDER_ID, "SHIPPING", ACTOR_ID, "ADMIN"));
        }

        @Test
        @DisplayName("CANCELLED → CONFIRMED (terminal) — rejected")
        void terminal_rejected() {
            Order order = orderWith(OrderStatus.CANCELLED, List.of(item("p1", 1)));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(IllegalArgumentException.class,
                    () -> orderService.updateStatus(ORDER_ID, "CONFIRMED", ACTOR_ID, "ADMIN"));
        }

        @Test
        @DisplayName("Invalid status string — throws")
        void invalid_status_throws() {
            Order order = orderWith(OrderStatus.PENDING, List.of());
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThrows(IllegalArgumentException.class,
                    () -> orderService.updateStatus(ORDER_ID, "NOT_A_STATUS", ACTOR_ID, "ADMIN"));
        }

        @Test
        @DisplayName("Order not found — throws EntityNotFoundException")
        void order_not_found() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class,
                    () -> orderService.updateStatus(ORDER_ID, "CONFIRMED", ACTOR_ID, "ADMIN"));
        }
    }
}
