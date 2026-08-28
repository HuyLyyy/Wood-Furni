package com.woodfurni.order.service;

import com.woodfurni.auth.model.Address;
import com.woodfurni.auth.model.User;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.cart.model.Cart;
import com.woodfurni.cart.model.CartItem;
import com.woodfurni.cart.repository.CartRepository;
import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.inventory.exception.InsufficientStockException;
import com.woodfurni.inventory.service.InventoryService;
import com.woodfurni.notification.client.NotificationClient;
import com.woodfurni.order.dto.CheckoutRequest;
import com.woodfurni.order.dto.OrderResponse;
import com.woodfurni.order.dto.PaymentResponse;
import com.woodfurni.order.dto.ReceiveReturnRequest;
import com.woodfurni.order.dto.TrackingUpdateRequest;
import com.woodfurni.order.enums.OrderStatus;
import com.woodfurni.order.enums.PaymentMethod;
import com.woodfurni.order.enums.PaymentStatus;
import com.woodfurni.order.model.Order;
import com.woodfurni.order.model.OrderItem;
import com.woodfurni.order.model.ShippingAddress;
import com.woodfurni.order.model.StatusHistoryEntry;
import com.woodfurni.order.model.TrackingUpdate;
import com.woodfurni.order.repository.OrderRepository;
import com.woodfurni.promotion.dto.ValidatePromotionResponse;
import com.woodfurni.promotion.service.PromotionService;
import com.woodfurni.shipping.dto.ShippingCalculateResponse;
import com.woodfurni.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Order management service.
 *
 * Checkout flow (simplified, no distributed transaction needed):
 * Since MongoDB standalone does not support multi-document ACID transactions,
 * we implement compensating rollback manually:
 * 1. Reserve inventory for each cart item
 * 2. If any reservation fails → release all previously reserved items for this checkout
 * 3. Validate promotion if provided; if invalid → release all reservations and fail
 * 4. Save Order + Payment
 * 5. Increment promotion usage
 * 6. Clear cart
 *
 * State machine (enforced in updateStatus):
 * PENDING → CONFIRMED → PROCESSING → SHIPPING → DELIVERED
 *     │
 *     └─────────────────────→ CANCELLED (from PENDING or CONFIRMED only)
 * DELIVERED → RETURNED (within return window)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final PromotionService promotionService;
    private final NotificationClient notificationClient;
    private final ShippingService shippingService;

    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Full checkout flow with compensating rollback on failure.
     *
     * Steps:
     * 1. Load cart → fail if empty
     * 2. Load shipping address → resolve city for shipping fee calculation
     * 3. Reserve inventory for each cart item (rollback on any failure)
     * 4. Validate promotion if provided → rollback reservations on invalid code
     * 5. Calculate shipping fee from ShippingService (uses city + cart weight)
     * 6. Generate orderNumber: ORD-yyyyMMdd-xxxx
     * 7. Save Order with PENDING status and statusHistory entry
     * 8. Create Payment record (COD → PENDING; sandbox methods → SUCCESS + order CONFIRMED)
     * 9. Increment promotion usage count
     * 10. Clear user's cart
     * 11. Return OrderResponse
     */
    @Transactional
    public OrderResponse checkout(String userId, CheckoutRequest request) {
        // --- Step 1: Load cart ---
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found for user: " + userId));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty. Add items before checkout.");
        }

        // --- Step 1b: Resolve which cart items to checkout ---
        // - null/empty productIds in the request → checkout the entire cart
        //   (legacy behaviour preserved for callers that don't know about
        //   multi-item selection yet).
        // - non-empty list → checkout only items whose productId is in the
        //   list. Any cart item NOT in the list stays in the cart for a
        //   later checkout. We require a non-empty selection.
        List<CartItem> selectedCartItems;
        List<String> requestedProductIds = request.getProductIds();
        if (requestedProductIds == null || requestedProductIds.isEmpty()) {
            selectedCartItems = cart.getItems();
        } else {
            // Validate that every requested productId actually exists in the cart,
            // otherwise the customer probably has a stale view — surface a clear error.
            Set<String> cartProductIds = cart.getItems().stream()
                    .map(CartItem::getProductId)
                    .collect(Collectors.toSet());
            List<String> missing = requestedProductIds.stream()
                    .filter(id -> !cartProductIds.contains(id))
                    .collect(Collectors.toList());
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Một số sản phẩm không còn trong giỏ hàng: " + String.join(", ", missing));
            }
            selectedCartItems = cart.getItems().stream()
                    .filter(item -> requestedProductIds.contains(item.getProductId()))
                    .collect(Collectors.toList());
            if (selectedCartItems.isEmpty()) {
                throw new IllegalArgumentException(
                        "Vui lòng chọn ít nhất một sản phẩm để thanh toán.");
            }
        }

        // --- Step 2: Load shipping address (embedded inside User per spec Mục 3.2) ---
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        Address address = null;
        if (user.getAddresses() != null) {
            for (Address a : user.getAddresses()) {
                if (request.getAddressId().equals(a.getId())) {
                    address = a;
                    break;
                }
            }
        }
        if (address == null) {
            throw new EntityNotFoundException(
                    "Shipping address not found in user's profile: " + request.getAddressId());
        }

        ShippingAddress shippingAddress = ShippingAddress.builder()
                .label(address.getLabel())
                .line1(address.getLine1())
                .ward(address.getWard())
                .district(address.getDistrict())
                .city(address.getCity())
                .phone(address.getPhone())
                .build();

        // ── Step 5 OUTSIDE try: validate shipping fee BEFORE reserving inventory ──
        // If the area is unsupported, we bail out here WITHOUT touching inventory.
        // The exception message is returned directly to the customer.
        ShippingCalculateResponse shippingResult = shippingService.calculateFee(
                address.getCity(), address.getDistrict());
        BigDecimal shippingFee = shippingResult.getFee();

        // List of items we successfully reserved (for compensating rollback)
        List<ReservedItem> reservedItems = new ArrayList<>();
        boolean inventoryReserved = false;

        try {
            // --- Step 3: Reserve inventory for each selected cart item ---
            for (CartItem cartItem : selectedCartItems) {
                log.info("[OrderService] initStockIfAbsent called for productId={}", cartItem.getProductId());
                inventoryService.initStockIfAbsent(cartItem.getProductId());
                inventoryService.reserve(cartItem.getProductId(), cartItem.getQuantity());
                reservedItems.add(new ReservedItem(cartItem.getProductId(), cartItem.getQuantity()));
                inventoryReserved = true;  // set after each successful reserve so partial rollback works
            }

            BigDecimal discountAmount = BigDecimal.ZERO;

            // --- Step 4: Validate promotion if provided ---
            if (request.getPromotionCode() != null && !request.getPromotionCode().isBlank()) {
                BigDecimal subtotal = selectedCartItems.stream()
                        .map(item -> item.getSubtotal() != null ? item.getSubtotal() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                ValidatePromotionResponse promoResult = promotionService.validateAndCalculate(
                        request.getPromotionCode(), subtotal);

                if (!promoResult.isValid()) {
                    throw new IllegalArgumentException("Invalid promotion: " + promoResult.getMessage());
                }

                discountAmount = promoResult.getDiscountAmount();
            }

            // --- Step 6: Build order items snapshot from selected items only ---
            List<OrderItem> orderItems = selectedCartItems.stream()
                    .map(item -> OrderItem.builder()
                            .productId(item.getProductId())
                            .productName(item.getProductName())
                            .sku(item.getProductName())
                            .unitPrice(item.getUnitPrice())
                            .quantity(item.getQuantity())
                            .subtotal(item.getSubtotal())
                            .build())
                    .collect(Collectors.toList());

            BigDecimal subtotalAmount = selectedCartItems.stream()
                    .map(item -> item.getSubtotal() != null ? item.getSubtotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // totalAmount = subtotal - discount + shippingFee
            BigDecimal totalAmount = subtotalAmount
                    .subtract(discountAmount != null ? discountAmount : BigDecimal.ZERO)
                    .add(shippingFee != null ? shippingFee : BigDecimal.ZERO);
            if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
                totalAmount = BigDecimal.ZERO;
            }

            // --- Step 6b: Generate order number ---
            String orderNumber = generateOrderNumber();

            // --- Step 7: Create and save Order ---
            Order order = Order.builder()
                    .orderNumber(orderNumber)
                    .customerId(userId)
                    .items(orderItems)
                    .shippingAddress(shippingAddress)
                    .promotionCode(request.getPromotionCode())
                    .discountAmount(discountAmount)
                    .shippingFee(shippingFee)
                    .subtotalAmount(subtotalAmount)
                    .totalAmount(totalAmount)
                    .status(OrderStatus.PENDING)
                    .paymentStatus(PaymentStatus.UNPAID)
                    .statusHistory(new ArrayList<>())
                    .build();

            order.addStatusHistory(OrderStatus.PENDING.name(), userId);
            Order savedOrder = orderRepository.save(order);

            // --- Step 8: Create Payment ---
            PaymentResponse payment = paymentService.createPayment(
                    savedOrder.getId(),
                    request.getPaymentMethod(),
                    totalAmount);

            // Update payment status on order.
            //
            // IMPORTANT (business rule):
            //   Order.status is ALWAYS PENDING right after checkout(), regardless of
            //   payment method (COD, SANDBOX_CARD, SANDBOX_WALLET) or whether the
            //   sandbox payment succeeded immediately.
            //
            //   The PENDING → CONFIRMED transition MUST go through
            //   POST /api/v1/orders/{id}/confirm (SALES / ADMIN) so a staff member
            //   explicitly acknowledges the order. There is no auto-confirm path
            //   anywhere in checkout() — this is the single source of truth.
            //
            //   Only paymentStatus is method-dependent:
            //     - COD                    → UNPAID (customer pays on delivery)
            //     - SANDBOX_* + SUCCESS    → PAID    (sandbox auto-succeeds)
            //     - SANDBOX_* + PENDING    → UNPAID  (defensive; sandbox may vary)
            PaymentStatus resultingPaymentStatus =
                    payment.getStatus() == PaymentStatus.SUCCESS
                            ? PaymentStatus.PAID
                            : PaymentStatus.UNPAID;
            savedOrder.setPaymentStatus(resultingPaymentStatus);
            savedOrder = orderRepository.save(savedOrder);
            // Order.status is left as-is (PENDING, set on creation above).

            // --- Step 9: Increment promotion usage ---
            if (request.getPromotionCode() != null && !request.getPromotionCode().isBlank()) {
                promotionService.incrementUsage(request.getPromotionCode());
            }

            // --- Step 10: Remove checked-out items from cart ---
            // If the customer only checked out a subset, the rest of the cart
            // stays intact for a later checkout. We recompute cart totals to
            // mirror what the client expects on the next render.
            if (requestedProductIds == null || requestedProductIds.isEmpty()) {
                cart.setItems(new ArrayList<>());
                cart.setTotalAmount(BigDecimal.ZERO);
            } else {
                Set<String> selectedIds = new HashSet<>(requestedProductIds);
                List<CartItem> remaining = cart.getItems().stream()
                        .filter(item -> !selectedIds.contains(item.getProductId()))
                        .collect(Collectors.toList());
                BigDecimal remainingTotal = remaining.stream()
                        .map(item -> item.getSubtotal() != null ? item.getSubtotal() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                cart.setItems(remaining);
                cart.setTotalAmount(remainingTotal);
            }
            cartRepository.save(cart);

            // --- Step 11: Realtime notify admins about new order ---
            notificationClient.notifyOrderCreated(
                    savedOrder.getId(),
                    savedOrder.getOrderNumber(),
                    savedOrder.getTotalAmount());

            // --- Step 12: Return response ---
            return toResponse(savedOrder, request.getPaymentMethod(), payment.getStatus());

        } catch (InsufficientStockException e) {
            log.warn("Checkout failed - insufficient stock: {}", e.getMessage());
            if (inventoryReserved) {
                rollbackReservations(reservedItems);
            }
            throw new IllegalArgumentException("Insufficient stock: " + e.getMessage());
        } catch (Exception e) {
            log.error("Checkout failed for user {}: {}", userId, e.getMessage(), e);
            if (inventoryReserved) {
                rollbackReservations(reservedItems);
            }
            throw e;
        }
    }

    /**
     * Rollback previously reserved inventory items.
     * This is the compensating rollback for partial checkout failures.
     */
    private void rollbackReservations(List<ReservedItem> reservedItems) {
        for (ReservedItem item : reservedItems) {
            try {
                inventoryService.release(item.productId, item.quantity);
                log.info("Rolled back reservation: productId={}, qty={}", item.productId, item.quantity);
            } catch (Exception ex) {
                log.error("Failed to rollback reservation for product {}: {}",
                        item.productId, ex.getMessage());
            }
        }
    }

    /**
     * Generate unique order number: ORD-yyyyMMdd-xxxx
     * Format: ORD-20260819-0001
     *
     * Strategy: count orders created today, increment by 1.
     * Thread-safe via MongoDB unique index on orderNumber.
     */
    private String generateOrderNumber() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String dateStr = today.format(ORDER_DATE_FORMAT);

        Instant startOfDay = today.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        long todayCount = orderRepository.countByCreatedAtBetween(startOfDay, endOfDay);

        return String.format("ORD-%s-%04d", dateStr, todayCount + 1);
    }

    /**
     * Get orders with role-based filtering and search.
     * - CUSTOMER: only their own orders
     * - SALES / ADMIN: all orders, optionally filtered by status, customerId, or orderNumber
     *
     * @param orderNumber if provided, searches orders:
     *   - Single value: partial match (case-insensitive), e.g., "ORD-20260826" matches "ORD-20260826-0001"
     *   - Multiple values separated by comma or space: exact match for each value
     *     e.g., "ORD-001, ORD-002" or "ORD-001 ORD-002"
     */
    public Page<OrderResponse> getOrders(String userId, boolean isStaff, String status,
                                        String customerId, String orderNumber,
                                        Instant createdFrom, Instant createdTo,
                                        int page, int size) {
        log.info("getOrders called - userId: {}, isStaff: {}, status: {}, customerId: {}, orderNumber: {}, createdFrom: {}, createdTo: {}",
                userId, isStaff, status, customerId, orderNumber, createdFrom, createdTo);

        Pageable pageable = PageRequest.of(page, size);

        if (!isStaff) {
            log.info("User is not staff, returning only their own orders");
            if (createdFrom != null || createdTo != null) {
                return findByCustomerIdWithDate(userId, createdFrom, createdTo, pageable);
            }
            return orderRepository.findByCustomerId(userId, pageable)
                    .map(order -> toResponse(order, null, null));
        }

        // orderNumber search takes priority if provided
        if (orderNumber != null && !orderNumber.isBlank()) {
            String trimmed = orderNumber.trim();
            log.info("Searching by orderNumber: {}", trimmed);

            // Check if input contains multiple order numbers (separated by comma or space)
            if (trimmed.contains(",") || trimmed.contains(" ")) {
                List<String> orderNumbers = Arrays.asList(
                    trimmed.split("[,\\s]+")
                ).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
                log.info("Multiple orderNumbers detected: {}", orderNumbers);

                if (!orderNumbers.isEmpty()) {
                    List<Order> orders = filterByDate(orderRepository.findByOrderNumberIn(orderNumbers), createdFrom, createdTo);
                    log.info("Found {} orders by orderNumberIn after date filter", orders.size());
                    int start = (int) pageable.getOffset();
                    int end = Math.min(start + pageable.getPageSize(), orders.size());
                    List<Order> pagedOrders = start < orders.size()
                        ? orders.subList(start, end)
                        : Collections.emptyList();
                    List<OrderResponse> pagedResponses = pagedOrders.stream()
                        .map(order -> toResponse(order, null, null))
                        .collect(Collectors.toList());
                    return new PageImpl<>(pagedResponses, pageable, orders.size());
                }
            }

            // Single orderNumber: partial match (case-insensitive)
            log.info("Single orderNumber search (partial match)");
            Page<Order> matched = orderRepository.findByOrderNumberContaining(trimmed, pageable);
            if (createdFrom == null && createdTo == null) {
                return matched.map(order -> toResponse(order, null, null));
            }
            // Manual date filter
            List<OrderResponse> filtered = matched.getContent().stream()
                    .filter(o -> isInDateRange(o.getCreatedAt(), createdFrom, createdTo))
                    .map(order -> toResponse(order, null, null))
                    .collect(Collectors.toList());
            return new PageImpl<>(filtered, pageable, filtered.size());
        }

        if (status != null && !status.isBlank()) {
            log.info("Searching by status: {}", status);
            OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
            if (customerId != null && !customerId.isBlank()) {
                log.info("Searching by customerId AND status");
                Page<Order> pageResult = orderRepository.findByCustomerIdAndStatus(customerId, orderStatus, pageable);
                if (createdFrom == null && createdTo == null) {
                    return pageResult.map(order -> toResponse(order, null, null));
                }
                List<OrderResponse> filtered = pageResult.getContent().stream()
                        .filter(o -> isInDateRange(o.getCreatedAt(), createdFrom, createdTo))
                        .map(order -> toResponse(order, null, null))
                        .collect(Collectors.toList());
                return new PageImpl<>(filtered, pageable, filtered.size());
            }
            log.info("Searching by status only");
            Page<Order> pageResult = orderRepository.findByStatus(orderStatus, pageable);
            if (createdFrom == null && createdTo == null) {
                return pageResult.map(order -> toResponse(order, null, null));
            }
            List<OrderResponse> filtered = pageResult.getContent().stream()
                    .filter(o -> isInDateRange(o.getCreatedAt(), createdFrom, createdTo))
                    .map(order -> toResponse(order, null, null))
                    .collect(Collectors.toList());
            return new PageImpl<>(filtered, pageable, filtered.size());
        }

        if (customerId != null && !customerId.isBlank()) {
            log.info("Searching by customerId: {}", customerId);

            String searchValue = customerId.trim().toLowerCase();
            String resolvedCustomerId = null;

            // 1) Try exact ObjectId match
            if (searchValue.length() == 24) {
                resolvedCustomerId = searchValue;
                log.info("Resolved as full ObjectId: {}", resolvedCustomerId);
            } else {
                // 2) Try exact customerCode match (e.g. #5f66 or 5f66)
                final String codeToFind = searchValue.startsWith("#") ? searchValue.substring(1) : searchValue;
                Optional<User> byCode = userRepository.findByCustomerCode(codeToFind);
                if (byCode.isPresent()) {
                    resolvedCustomerId = byCode.get().getId();
                    log.info("Resolved by customerCode {} -> {}", codeToFind, resolvedCustomerId);
                } else {
                    // 3) Try suffix match on ObjectId
                    List<Order> allOrders = filterByDate(orderRepository.findAll(), createdFrom, createdTo);
                    List<Order> suffixMatches = allOrders.stream()
                        .filter(o -> o.getCustomerId() != null && o.getCustomerId().endsWith(searchValue))
                        .collect(Collectors.toList());
                    if (!suffixMatches.isEmpty()) {
                        log.info("Found {} orders by suffix match on customerId (after date filter)", suffixMatches.size());
                        int start = (int) pageable.getOffset();
                        int end = Math.min(start + pageable.getPageSize(), suffixMatches.size());
                        List<Order> pagedOrders = start < suffixMatches.size()
                            ? suffixMatches.subList(start, end)
                            : Collections.emptyList();
                        List<OrderResponse> pagedResponses = pagedOrders.stream()
                            .map(order -> toResponse(order, null, null))
                            .collect(Collectors.toList());
                        return new PageImpl<>(pagedResponses, pageable, suffixMatches.size());
                    }
                }
            }

            if (resolvedCustomerId != null) {
                Page<Order> pageResult = orderRepository.findByCustomerId(resolvedCustomerId, pageable);
                if (createdFrom == null && createdTo == null) {
                    return pageResult.map(order -> toResponse(order, null, null));
                }
                List<OrderResponse> filtered = pageResult.getContent().stream()
                        .filter(o -> isInDateRange(o.getCreatedAt(), createdFrom, createdTo))
                        .map(order -> toResponse(order, null, null))
                        .collect(Collectors.toList());
                return new PageImpl<>(filtered, pageable, filtered.size());
            }

            log.warn("No match found for customerId: {}", customerId);
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        // No specific filter - apply date filter on all orders if provided
        log.info("No filters, returning all orders (with optional date filter)");
        Page<Order> allOrdersPage = orderRepository.findAll(pageable);
        if (createdFrom == null && createdTo == null) {
            return allOrdersPage.map(order -> toResponse(order, null, null));
        }
        List<OrderResponse> filtered = allOrdersPage.getContent().stream()
                .filter(o -> isInDateRange(o.getCreatedAt(), createdFrom, createdTo))
                .map(order -> toResponse(order, null, null))
                .collect(Collectors.toList());
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    /**
     * Get single order. Owner or admin/sales can view.
     */
    public OrderResponse getOrderById(String orderId, String userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (!isAdmin && !order.getCustomerId().equals(userId)) {
            throw new IllegalArgumentException("Access denied to this order");
        }

        return toResponse(order, null, null);
    }

    /**
     * Update order status with state machine + role-based transition enforcement.
     *
     * Role-based transitions (ADMIN bypasses all role checks):
     * - PENDING → CONFIRMED:  SALES, ADMIN
     * - CONFIRMED → PROCESSING: SALES, ADMIN  ("gửi đơn qua Warehouse")
     * - PROCESSING → SHIPPING: WAREHOUSE, ADMIN ("đã chuẩn bị/đóng gói xong")
     * - SHIPPING → DELIVERED:  SALES, WAREHOUSE, ADMIN
     * - CANCELLED:             chủ đơn khi PENDING/CONFIRMED, hoặc ADMIN
     *
     * Side effects:
     * - CANCELLED → InventoryService.release() for each item
     * - DELIVERED → InventoryService.commit() for each item
     *
     * @param role ROLE name (ADMIN, SALES, WAREHOUSE, CONTENT) — ADMIN always allowed
     */
    @Transactional
    public OrderResponse updateStatus(String orderId, String newStatus, String actorUserId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        OrderStatus target;
        try {
            target = OrderStatus.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + newStatus);
        }

        OrderStatus current = order.getStatus();

        if (!isValidTransition(current, target, role)) {
            throw new IllegalArgumentException(getTransitionErrorMessage(current, target, role));
        }

        // Handle side effects on specific transitions
        if (target == OrderStatus.CANCELLED) {
            releaseInventoryForOrder(order);
        } else if (target == OrderStatus.DELIVERED) {
            commitInventoryForOrder(order);
        }

        order.setStatus(target);
        order.addStatusHistory(target.name(), actorUserId);

        if (target == OrderStatus.CANCELLED) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        Order saved = orderRepository.save(order);

        // Realtime notify the customer that their order status changed.
        notificationClient.notifyOrderStatus(
                saved.getId(),
                saved.getOrderNumber(),
                saved.getStatus().name(),
                saved.getCustomerId());

        // Notify WAREHOUSE sockets when order is sent for preparation (CONFIRMED → PROCESSING).
        if (saved.getStatus() == OrderStatus.PROCESSING) {
            notificationClient.notifyOrderSentToWarehouse(
                    saved.getId(),
                    saved.getOrderNumber(),
                    saved.getItems() != null ? saved.getItems().size() : 0);
        }

        return toResponse(saved, null, null);
    }

    /**
     * Send order to warehouse — CONFIRMED → PROCESSING.
     * Role: SALES or ADMIN only.
     */
    @Transactional
    public OrderResponse sendToWarehouse(String orderId, String actorUserId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalArgumentException(
                    "Chỉ đơn hàng ở trạng thái CONFIRMED mới có thể gửi qua Warehouse. Trạng thái hiện tại: "
                            + order.getStatus().name());
        }

        if (!"ADMIN".equals(role) && !"SALES".equals(role)) {
            throw new IllegalArgumentException("Chỉ Sales mới được gửi đơn qua Warehouse.");
        }

        order.setStatus(OrderStatus.PROCESSING);
        order.addStatusHistory(OrderStatus.PROCESSING.name(), actorUserId);
        Order saved = orderRepository.save(order);

        notificationClient.notifyOrderStatus(
                saved.getId(), saved.getOrderNumber(), saved.getStatus().name(), saved.getCustomerId());

        notificationClient.notifyOrderSentToWarehouse(
                saved.getId(),
                saved.getOrderNumber(),
                saved.getItems() != null ? saved.getItems().size() : 0);

        return toResponse(saved, null, null);
    }

    /**
     * Mark order as prepared — PROCESSING → SHIPPING.
     * Role: WAREHOUSE or ADMIN only.
     */
    @Transactional
    public OrderResponse markPrepared(String orderId, String actorUserId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new IllegalArgumentException(
                    "Chỉ đơn hàng ở trạng thái PROCESSING mới có thể đánh dấu đã chuẩn bị xong. Trạng thái hiện tại: "
                            + order.getStatus().name());
        }

        if (!"ADMIN".equals(role) && !"WAREHOUSE".equals(role)) {
            throw new IllegalArgumentException("Chỉ nhân viên Warehouse mới được đánh dấu đơn đã chuẩn bị xong.");
        }

        order.setStatus(OrderStatus.SHIPPING);
        order.addStatusHistory(OrderStatus.SHIPPING.name(), actorUserId);
        Order saved = orderRepository.save(order);

        notificationClient.notifyOrderStatus(
                saved.getId(), saved.getOrderNumber(), saved.getStatus().name(), saved.getCustomerId());

        return toResponse(saved, null, null);
    }

    /**
     * Append a tracking event to an order currently in SHIPPING.
     *
     * Behavior:
     * - Only allowed when {@code order.status == SHIPPING}; otherwise throws
     *   a clear error ("Chỉ cập nhật tracking khi đơn đang giao").
     * - Pushes a new {@link TrackingUpdate} entry.
     * - If {@code request.isDelivered == true}, after pushing the tracking
     *   entry the order transitions SHIPPING → DELIVERED in the SAME call:
     *     - {@link InventoryService#commit(String, int)} for each item
     *     - appends a statusHistory entry for DELIVERED
     *     - emits the same realtime notification as {@link #updateStatus}
     * - Always (regardless of isDelivered) emits a realtime notification so
     *   the customer's timeline updates in real time even when the status
     *   itself doesn't change yet.
     *
     * @param orderId      target order
     * @param request      tracking event payload
     * @param actorUserId  user ID of the staff recording this update
     * @return updated OrderResponse (with trackingUpdates + status)
     */
    @Transactional
    public OrderResponse addTrackingUpdate(String orderId, TrackingUpdateRequest request,
                                           String actorUserId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new IllegalArgumentException(
                    "Chỉ cập nhật tracking khi đơn đang giao. Trạng thái hiện tại: "
                            + order.getStatus().name());
        }

        // Always push the tracking entry first so even mid-shipment updates
        // appear on the customer timeline.
        TrackingUpdate entry = TrackingUpdate.builder()
                .status(request.getStatus())
                .location(request.getLocation())
                .note(request.getNote())
                .updatedAt(Instant.now())
                .updatedBy(actorUserId)
                .build();
        order.addTrackingUpdate(entry);

        OrderStatus newStatus = order.getStatus();
        if (Boolean.TRUE.equals(request.getIsDelivered())) {
            // Reuse the SAME logic as updateStatus(SHIPPING → DELIVERED): commit
            // inventory, push statusHistory, then notify.
            commitInventoryForOrder(order);
            order.setStatus(OrderStatus.DELIVERED);
            order.addStatusHistory(OrderStatus.DELIVERED.name(), actorUserId);
            newStatus = OrderStatus.DELIVERED;
        }

        Order saved = orderRepository.save(order);

        // Realtime notify the customer for every tracking update — both
        // intermediate ("Đã lấy hàng", ...) and the final delivered one.
        // We reuse the existing order.status.updated channel: the gateway
        // already routes this to their personal room, and the frontend can
        // refresh its timeline from the returned order payload.
        try {
            notificationClient.notifyOrderStatus(
                    saved.getId(),
                    saved.getOrderNumber(),
                    newStatus.name(),
                    saved.getCustomerId());
        } catch (Exception ex) {
            log.warn("Failed to notify tracking update for order {}: {}",
                    saved.getId(), ex.getMessage());
        }

        return toResponse(saved, null, null);
    }

    /**
     * Read-only accessor used by GET endpoint to return a customer's
     * shipment timeline. Caller is expected to have already enforced that
     * the actor is the order owner or has staff role.
     */
    public List<TrackingUpdate> getTrackingUpdates(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        return order.getTrackingUpdates();
    }

    /**
     * Get the shipment timeline for a single order, enforcing that the
     * caller is either the order owner or has a staff role.
     *
     * @param orderId target order
     * @param actorUserId userId from the JWT principal
     * @param isStaff true if the caller has SALES/WAREHOUSE/ADMIN role
     */
    public List<TrackingUpdate> getTrackingUpdatesForUser(String orderId, String actorUserId,
                                                          boolean isStaff) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (!isStaff && !order.getCustomerId().equals(actorUserId)) {
            throw new IllegalArgumentException(
                    "Bạn không có quyền xem tracking của đơn hàng này.");
        }

        return order.getTrackingUpdates();
    }

    /**
     * Cancel order by customer (only PENDING/CONFIRMED) or admin.
     */
    @Transactional
    public OrderResponse cancelOrder(String orderId, String userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (!isAdmin && !order.getCustomerId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only PENDING or CONFIRMED orders can be cancelled");
        }

        return updateStatus(orderId, OrderStatus.CANCELLED.name(), userId, isAdmin ? "ADMIN" : "CUSTOMER");
    }

    /**
     * Admin-only forced cancel for orders that already left PENDING/CONFIRMED
     * (i.e. SHIPPING or DELIVERED).
     *
     * Rule: only allowed when the order has a promotion code, because the
     * otherwise-applied partial-receipt logic doesn't fit an order where
     * the discount was already applied to the full subtotal. There are two
     * scenarios in the wild:
     *
     * - SHIPPING + has promotion → customer hasn't received yet → release
     *   inventory, refund, mark CANCELLED.
     * - DELIVERED + has promotion → customer already received → still
     *   mark CANCELLED + refund, but we do NOT release inventory (it was
     *   committed on the original DELIVERED transition).
     *
     * Either way, the business outcome is "the order is now void and the
     * customer is refunded" — handled by the side effects in
     * {@link #updateStatus} for the CANCELLED transition.
     */
    public OrderResponse forceCancelWithPromotion(String orderId, String actorUserId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        boolean hasPromotion = order.getPromotionCode() != null
                && !order.getPromotionCode().isBlank()
                && order.getDiscountAmount() != null
                && order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0;
        if (!hasPromotion) {
            throw new IllegalArgumentException(
                    "Chỉ đơn hàng có mã khuyến mãi mới được hủy sau khi đã giao/đang giao.");
        }

        OrderStatus current = order.getStatus();
        if (current != OrderStatus.SHIPPING && current != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException(
                    "Chỉ hỗ trợ hủy đơn đang ở trạng thái SHIPPING hoặc DELIVERED. "
                            + "Trạng thái hiện tại: " + current.name());
        }

        OrderStatus target = OrderStatus.CANCELLED;

        // For SHIPPING we must release inventory (it was reserved, not committed).
        // For DELIVERED we must NOT release inventory (it was committed already).
        if (current == OrderStatus.SHIPPING) {
            releaseInventoryForOrder(order);
        }

        order.setStatus(target);
        order.addStatusHistory(target.name(), actorUserId);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        if (reason != null && !reason.isBlank()) {
            order.addStatusHistory(
                    target.name() + " — Lý do: " + reason,
                    actorUserId);
        }

        Order saved = orderRepository.save(order);

        log.info("[forceCancelWithPromotion] Order {} ({}) → CANCELLED by {} (was {})",
                saved.getOrderNumber(), saved.getId(), actorUserId, current);

        notificationClient.notifyOrderStatus(
                saved.getId(),
                saved.getOrderNumber(),
                saved.getStatus().name(),
                saved.getCustomerId());

        return toResponse(saved, null, null);
    }

    /**
     * Role-aware state-transition guard for the generic
     * {@code PATCH /orders/{id}/status} endpoint.
     *
     * Each transition is gated by the role that owns it; this matches the
     * dedicated convenience endpoints ({@code send-to-warehouse},
     * {@code mark-prepared}, {@code tracking-updates}) so a caller cannot
     * bypass role enforcement by hitting the generic endpoint instead.
     *
     *   - PENDING → CONFIRMED         : SALES | ADMIN
     *   - PENDING → CANCELLED         : CUSTOMER | ADMIN
     *   - CONFIRMED → PROCESSING      : SALES | ADMIN   (gửi qua Warehouse)
     *   - CONFIRMED → CANCELLED       : ADMIN
     *   - PROCESSING → SHIPPING       : WAREHOUSE | ADMIN  (đã chuẩn bị xong)
     *                                   → SALES KHÔNG được tự ý chuyển sang SHIPPING.
     *                                     Họ phải đợi Warehouse xác nhận đã chuẩn bị xong,
     *                                     rồi mới được thao tác tiếp tracking / "đang giao".
     *   - SHIPPING → DELIVERED        : SALES | WAREHOUSE | ADMIN
     *                                   (typically via tracking-updates with isDelivered=true,
     *                                    but generic endpoint also accepts it for the same roles)
     *   - DELIVERED                   : terminal — no further transitions
     *   - CANCELLED, RETURNED → *     : no transitions allowed
     *
     * ADMIN retains the catch-all bypass for legitimate ops overrides.
     */
    private boolean isValidTransition(OrderStatus from, OrderStatus to, String role) {
        if (from == to) return false;
        boolean isAdmin = "ADMIN".equals(role);

        // ADMIN catch-all — any structurally valid transition is allowed.
        if (isAdmin) {
            return switch (from) {
                case PENDING -> to == OrderStatus.CONFIRMED || to == OrderStatus.CANCELLED;
                case CONFIRMED -> to == OrderStatus.PROCESSING || to == OrderStatus.CANCELLED;
                case PROCESSING -> to == OrderStatus.SHIPPING;
                case SHIPPING -> to == OrderStatus.DELIVERED;
                case DELIVERED -> false;
                case CANCELLED, RETURNED -> false;
            };
        }

        // Role-gated transitions for non-admin roles.
        return switch (from) {
            case PENDING -> {
                if (to == OrderStatus.CONFIRMED) {
                    yield "SALES".equals(role); // SALES confirms PENDING orders
                }
                if (to == OrderStatus.CANCELLED) {
                    yield "CUSTOMER".equals(role); // customer self-cancels PENDING
                }
                yield false;
            }
            case CONFIRMED -> {
                if (to == OrderStatus.PROCESSING) {
                    // CONFIRMED → PROCESSING is exclusively owned by the
                    // dedicated POST /orders/{id}/send-to-warehouse endpoint
                    // (guarded by @PreAuthorize("hasAnyRole('SALES','ADMIN')")).
                    // Generic PATCH /orders/{id}/status must REJECT this
                    // transition for every role, so callers have to go through
                    // the dedicated endpoint that carries the explicit
                    // "Sales hands the order over to Warehouse" semantics
                    // and emits the matching realtime notify.
                    yield false;
                }
                if (to == OrderStatus.CANCELLED) {
                    yield false; // only ADMIN can cancel CONFIRMED
                }
                yield false;
            }
            case PROCESSING -> {
                if (to == OrderStatus.SHIPPING) {
                    // CRITICAL: PROCESSING → SHIPPING is exclusively owned by the
                    // dedicated POST /orders/{id}/mark-prepared endpoint (guarded
                    // by @PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")).
                    // Generic PATCH /orders/{id}/status must REJECT this transition
                    // for every role, forcing callers through the dedicated endpoint.
                    //
                    // Business rule: after Sales "gửi qua Warehouse"
                    // (CONFIRMED → PROCESSING), Sales must NOT be able to flip
                    // the order to SHIPPING themselves. They have to wait for
                    // Warehouse Staff to confirm "đã chuẩn bị xong" via
                    // /mark-prepared before Sales can resume shipping-related
                    // actions (tracking / "đang giao" / DELIVERED).
                    yield false;
                }
                yield false;
            }
            case SHIPPING -> {
                if (to == OrderStatus.DELIVERED) {
                    // SALES (or WAREHOUSE, or ADMIN) confirms delivery.
                    // This is also reachable via tracking-updates?isDelivered=true,
                    // but the generic endpoint mirrors the same role allow-list.
                    yield "SALES".equals(role) || "WAREHOUSE".equals(role);
                }
                yield false;
            }
            case DELIVERED -> {
                // DELIVERED is terminal — no further transitions allowed
                yield false;
            }
            case CANCELLED, RETURNED -> false;
        };
    }

    private String getTransitionErrorMessage(OrderStatus from, OrderStatus to, String role) {
        boolean isAdmin = "ADMIN".equals(role);

        // ADMIN bypasses all role checks; if ADMIN fails it must be invalid transition
        if (isAdmin) {
            return String.format("Invalid status transition: %s → %s", from.name(), to.name());
        }

        // Role-specific messages
        return switch (from) {
            case PENDING -> {
                if (to == OrderStatus.CONFIRMED && !"SALES".equals(role) && !"ADMIN".equals(role))
                    yield "Chỉ Sales mới được xác nhận đơn hàng.";
                yield String.format("Invalid status transition: %s → %s", from.name(), to.name());
            }
            case CONFIRMED -> {
                if (to == OrderStatus.PROCESSING && !"SALES".equals(role) && !"ADMIN".equals(role))
                    yield "Chỉ Sales mới được gửi đơn qua Warehouse.";
                if (to == OrderStatus.CANCELLED && !"ADMIN".equals(role))
                    yield "Chỉ Admin mới được hủy đơn ở trạng thái CONFIRMED.";
                yield String.format("Invalid status transition: %s → %s", from.name(), to.name());
            }
            case PROCESSING -> {
                if (to == OrderStatus.SHIPPING && !"WAREHOUSE".equals(role) && !"ADMIN".equals(role))
                    yield "Chỉ nhân viên Warehouse mới được đánh dấu đơn đã chuẩn bị xong.";
                yield String.format("Invalid status transition: %s → %s", from.name(), to.name());
            }
            case SHIPPING -> {
                if (to == OrderStatus.DELIVERED
                        && !"SALES".equals(role) && !"WAREHOUSE".equals(role) && !"ADMIN".equals(role))
                    yield "Chỉ Sales hoặc Warehouse mới được xác nhận giao hàng thành công.";
                yield String.format("Invalid status transition: %s → %s", from.name(), to.name());
            }
            default -> String.format("Invalid status transition: %s → %s", from.name(), to.name());
        };
    }

    /**
     * Nhận lại hàng từ nhân viên giao hàng (NVGH).
     *
     * Business rules:
     * - Only orders in SHIPPING status can be received
     * - Role: SALES or ADMIN only
     * - Two scenarios:
     *   1. All items have receivedQuantity = 0 → order becomes CANCELLED
     *   2. Any item has receivedQuantity > 0 → order becomes DELIVERED with adjusted quantities
     *
     * Side effects:
     * - Updates receivedQuantity on each item
     * - Recalculates subtotalAmount and totalAmount
     * - Releases inventory for rejected items (receivedQuantity < ordered quantity)
     * - Commits inventory for accepted items (receivedQuantity)
     * - Adds status history entry with note
     *
     * @param orderId order to receive
     * @param request list of received quantities per item index
     * @param actorUserId user performing the action
     * @return updated order
     */
    @Transactional
    public OrderResponse receiveReturn(String orderId, ReceiveReturnRequest request, String actorUserId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new IllegalArgumentException(
                    "Chỉ đơn hàng ở trạng thái SHIPPING mới có thể nhận lại hàng. Trạng thái hiện tại: "
                            + order.getStatus().name());
        }

        boolean hasPromotion = order.getPromotionCode() != null
                && !order.getPromotionCode().isBlank()
                && order.getDiscountAmount() != null
                && order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0;

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Danh sách sản phẩm không hợp lệ.");
        }

        // Build a map of itemIndex -> receivedQuantity for quick lookup
        Map<Integer, Integer> receiptMap = request.getItems().stream()
                .collect(Collectors.toMap(
                        ReceiveReturnRequest.ItemReceipt::getItemIndex,
                        ReceiveReturnRequest.ItemReceipt::getReceivedQuantity,
                        (a, b) -> a // keep first on duplicate
                ));

        // Validate and update each item
        boolean allZero = true;
        boolean allFull = true;
        for (int i = 0; i < order.getItems().size(); i++) {
            OrderItem item = order.getItems().get(i);
            Integer receivedQty = receiptMap.get(i);

            if (receivedQty == null) {
                throw new IllegalArgumentException(
                        "Thiếu số lượng cho sản phẩm thứ " + (i + 1) + ": " + item.getProductName());
            }

            if (receivedQty < 0 || receivedQty > item.getQuantity()) {
                throw new IllegalArgumentException(
                        "Số lượng nhận không hợp lệ cho sản phẩm " + item.getProductName()
                                + ": phải từ 0 đến " + item.getQuantity());
            }

            item.setReceivedQuantity(receivedQty);

            if (receivedQty > 0) {
                allZero = false;
            }
            if (receivedQty < item.getQuantity()) {
                allFull = false;
            }
        }

        // Promotion rule: if order has a promotion code, the customer must
        // either accept EVERY item in full (→ DELIVERED) or reject EVERY
        // item (→ CANCELLED). A mixed state (some full, some zero, or any
        // in-between) is not allowed because the discount math no longer
        // holds for a partial delivery.
        if (hasPromotion && !allZero && !allFull) {
            throw new IllegalArgumentException(
                    "Đơn hàng có mã khuyến mãi '" + order.getPromotionCode()
                            + "' không thể nhận một phần. Phải nhận đủ tất cả hoặc trả lại toàn bộ.");
        }

        // Recalculate totals based on received quantities
        recalculateOrderAmounts(order);

        // Determine final status
        OrderStatus finalStatus;
        String historyNote = request.getNote();

        if (allZero) {
            // Customer rejected all items → CANCELLED
            finalStatus = OrderStatus.CANCELLED;
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            releaseInventoryForOrder(order);
            log.info("[receiveReturn] Order {} fully rejected → CANCELLED", order.getOrderNumber());
        } else {
            // Customer accepted some items → DELIVERED
            finalStatus = OrderStatus.DELIVERED;
            commitReceivedInventory(order);
            log.info("[receiveReturn] Order {} partially/full accepted → DELIVERED", order.getOrderNumber());
        }

        order.setStatus(finalStatus);
        order.addStatusHistory(finalStatus.name(), actorUserId);

        // Add tracking update if there's a note
        if (historyNote != null && !historyNote.isBlank()) {
            TrackingUpdate receiptNote = TrackingUpdate.builder()
                    .status("RECEIVED")
                    .note(historyNote)
                    .updatedAt(Instant.now())
                    .updatedBy(actorUserId)
                    .build();
            order.addTrackingUpdate(receiptNote);
        }

        Order saved = orderRepository.save(order);

        // Notify customer
        notificationClient.notifyOrderStatus(
                saved.getId(),
                saved.getOrderNumber(),
                saved.getStatus().name(),
                saved.getCustomerId());

        return toResponse(saved, null, null);
    }

    /**
     * Recalculate subtotalAmount and totalAmount based on received quantities.
     * Only counts items where receivedQuantity > 0.
     */
    private void recalculateOrderAmounts(Order order) {
        BigDecimal newSubtotal = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            if (item.getReceivedQuantity() != null && item.getReceivedQuantity() > 0) {
                BigDecimal receivedSubtotal = item.getUnitPrice()
                        .multiply(BigDecimal.valueOf(item.getReceivedQuantity()));
                item.setSubtotal(receivedSubtotal);
                newSubtotal = newSubtotal.add(receivedSubtotal);
            } else {
                // Mark rejected items as 0 subtotal
                item.setSubtotal(BigDecimal.ZERO);
            }
        }

        order.setSubtotalAmount(newSubtotal);
        // totalAmount = subtotal - discount + shippingFee (shipping fee still applies)
        BigDecimal newTotal = newSubtotal
                .subtract(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                .add(order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO);
        if (newTotal.compareTo(BigDecimal.ZERO) < 0) {
            newTotal = BigDecimal.ZERO;
        }
        order.setTotalAmount(newTotal);
    }

    /**
     * Commit inventory for received items only.
     * Unlike commitInventoryForOrder which uses the full ordered quantity,
     * this only commits what the customer actually accepted.
     */
    private void commitReceivedInventory(Order order) {
        for (OrderItem item : order.getItems()) {
            Integer receivedQty = item.getReceivedQuantity();
            if (receivedQty != null && receivedQty > 0) {
                try {
                    inventoryService.commit(item.getProductId(), receivedQty);
                    log.info("Committed received inventory for order {}: productId={}, receivedQty={}",
                            order.getOrderNumber(), item.getProductId(), receivedQty);
                } catch (Exception ex) {
                    log.error("Failed to commit inventory for product {}: {}",
                            item.getProductId(), ex.getMessage());
                }
            }
        }
    }

    private void releaseInventoryForOrder(Order order) {
        for (OrderItem item : order.getItems()) {
            try {
                inventoryService.release(item.getProductId(), item.getQuantity());
                log.info("Released inventory for cancelled order {}: productId={}, qty={}",
                        order.getOrderNumber(), item.getProductId(), item.getQuantity());
            } catch (Exception ex) {
                log.error("Failed to release inventory for product {}: {}",
                        item.getProductId(), ex.getMessage());
            }
        }
    }

    private void commitInventoryForOrder(Order order) {
        for (OrderItem item : order.getItems()) {
            try {
                inventoryService.commit(item.getProductId(), item.getQuantity());
                log.info("Committed inventory for delivered order {}: productId={}, qty={}",
                        order.getOrderNumber(), item.getProductId(), item.getQuantity());
            } catch (Exception ex) {
                log.error("Failed to commit inventory for product {}: {}",
                        item.getProductId(), ex.getMessage());
            }
        }
    }

    private OrderResponse toResponse(Order order, PaymentMethod paymentMethod, PaymentStatus paymentStatus) {
        PaymentResponse payment = null;
        if (order.getId() != null) {
            try {
                payment = paymentService.getByOrderId(order.getId());
                if (payment != null) {
                    paymentMethod = payment.getMethod();
                    paymentStatus = payment.getStatus();
                }
            } catch (Exception ignored) {
                // Payment lookup is best-effort. An order can legitimately exist
                // without a matching payment row in some edge cases (legacy data,
                // direct DB writes, etc.). Falling back to whatever is already on
                // the order itself is safer than letting an NPE kill the whole list.
            }
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerId(order.getCustomerId())
                .customerCode(resolveCustomerCode(order.getCustomerId()))
                .items(order.getItems())
                .shippingAddress(order.getShippingAddress())
                .promotionCode(order.getPromotionCode())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .subtotalAmount(order.getSubtotalAmount())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .paymentStatus(paymentStatus != null ? paymentStatus : order.getPaymentStatus())
                .paymentMethod(paymentMethod)
                .statusHistory(order.getStatusHistory())
                .trackingUpdates(order.getTrackingUpdates())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private String resolveCustomerCode(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return null;
        }
        try {
            var userOpt = userRepository.findById(customerId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (user.getCustomerCode() == null || user.getCustomerCode().isBlank()) {
                    // Auto-generate customerCode from last 6 chars of ObjectId
                    String autoCode = customerId.length() >= 6
                            ? customerId.substring(customerId.length() - 6)
                            : customerId;
                    user.setCustomerCode(autoCode);
                    userRepository.save(user);
                    log.info("Auto-generated customerCode {} for user {}", autoCode, customerId);
                    return autoCode;
                }
                return user.getCustomerCode();
            }
            return null;
        } catch (Exception ex) {
            log.debug("Failed to resolve customerCode for {}: {}", customerId, ex.getMessage());
            return null;
        }
    }

    private record ReservedItem(String productId, int quantity) {}

    /**
     * Filter orders by customerId + date range.
     * Used for non-staff users (customers) who also want date filtering.
     */
    private Page<OrderResponse> findByCustomerIdWithDate(String customerId, Instant from, Instant to, Pageable pageable) {
        List<Order> all = orderRepository.findAll().stream()
                .filter(o -> customerId.equals(o.getCustomerId()))
                .filter(o -> isInDateRange(o.getCreatedAt(), from, to))
                .collect(Collectors.toList());
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<Order> pagedOrders = start < all.size() ? all.subList(start, end) : Collections.emptyList();
        List<OrderResponse> responses = pagedOrders.stream()
                .map(order -> toResponse(order, null, null))
                .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, all.size());
    }

    /**
     * Check if an Instant is within the given date range.
     * Both bounds are inclusive. Null bounds mean "no limit on that side".
     */
    private boolean isInDateRange(Instant createdAt, Instant from, Instant to) {
        if (createdAt == null) return false;
        if (from != null && createdAt.isBefore(from)) return false;
        if (to != null && createdAt.isAfter(to)) return false;
        return true;
    }

    /**
     * Apply date filter to a list of orders.
     */
    private List<Order> filterByDate(List<Order> orders, Instant from, Instant to) {
        if (from == null && to == null) return orders;
        return orders.stream()
                .filter(o -> isInDateRange(o.getCreatedAt(), from, to))
                .collect(Collectors.toList());
    }
}
