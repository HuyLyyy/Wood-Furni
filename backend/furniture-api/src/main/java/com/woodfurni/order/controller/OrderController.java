package com.woodfurni.order.controller;

import com.woodfurni.common.ApiResponse;
import com.woodfurni.common.PageResponse;
import com.woodfurni.order.dto.CheckoutRequest;
import com.woodfurni.order.dto.OrderResponse;
import com.woodfurni.order.dto.ReceiveReturnRequest;
import com.woodfurni.order.dto.TrackingUpdateRequest;
import com.woodfurni.order.dto.UpdateOrderStatusRequest;
import com.woodfurni.order.model.TrackingUpdate;
import com.woodfurni.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management and checkout")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    private static String extractRole(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(a -> ((SimpleGrantedAuthority) a).getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("CUSTOMER");
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Checkout - create order from cart",
               description = "Reserves inventory, validates promotion, creates order + payment, clears cart. " +
                       "Supports COD (PENDING payment) and sandbox methods (auto SUCCESS).")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CheckoutRequest request) {
        String userId = userDetails.getUsername();
        OrderResponse order = orderService.checkout(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Order placed successfully", order));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES', 'WAREHOUSE', 'ADMIN')")
    @Operation(summary = "List orders",
               description = "CUSTOMER sees own orders only. SALES/WAREHOUSE/ADMIN see all orders with optional filters.")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrders(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = userDetails.getUsername();
        boolean isStaff = userDetails.getAuthorities().stream()
                .anyMatch(a -> {
                    String auth = ((SimpleGrantedAuthority) a).getAuthority();
                    return "ROLE_ADMIN".equals(auth) || "ROLE_SALES".equals(auth) || "ROLE_WAREHOUSE".equals(auth);
                });

        Page<OrderResponse> result = orderService.getOrders(userId, isStaff, status, customerId, orderNumber, createdFrom, createdTo, page, size);

        PageResponse<OrderResponse> pageResponse = PageResponse.<OrderResponse>builder()
                .items(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();

        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES', 'ADMIN')")
    @Operation(summary = "Get order by ID",
               description = "Order owner or SALES/ADMIN can view.")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {

        String userId = userDetails.getUsername();
        boolean isAdminOrSales = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(((SimpleGrantedAuthority) a).getAuthority())
                        || "ROLE_SALES".equals(((SimpleGrantedAuthority) a).getAuthority()));

        OrderResponse order = orderService.getOrderById(id, userId, isAdminOrSales);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    /**
     * Generic status update — role-based transition rules enforced in OrderService.
     * Returns descriptive error if the caller's role is not permitted for this transition.
     *
     * Role rules:
     * - PENDING → CONFIRMED: SALES, ADMIN
     * - CONFIRMED → PROCESSING: SALES, ADMIN
     * - PROCESSING → SHIPPING: WAREHOUSE, ADMIN
     * - SHIPPING → DELIVERED: SALES, WAREHOUSE, ADMIN
     * - Any → CANCELLED: ADMIN (or customer via /cancel endpoint)
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SALES', 'WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Update order status (role-enforced)",
               description = "Transitions follow the state machine. Each step restricts which roles can perform it. " +
                       "Returns a descriptive error (not 403) if the role is not permitted for the requested transition.")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {

        String userId = userDetails.getUsername();
        String role = extractRole(userDetails);
        OrderResponse order = orderService.updateStatus(id, request.getStatus(), userId, role);
        return ResponseEntity.ok(ApiResponse.success("Order status updated", order));
    }

    /**
     * Send order to warehouse — CONFIRMED → PROCESSING.
     * Convenience endpoint with clear business semantics.
     * Role: SALES or ADMIN only.
     */
    @PostMapping("/{id}/send-to-warehouse")
    @PreAuthorize("hasAnyRole('SALES', 'ADMIN')")
    @Operation(summary = "Send order to warehouse (CONFIRMED → PROCESSING)",
               description = "Sales marks the order as ready for warehouse preparation. " +
                       "Returns descriptive error if called by a non-Sales role or if order is not CONFIRMED.")
    public ResponseEntity<ApiResponse<OrderResponse>> sendToWarehouse(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        String userId = userDetails.getUsername();
        String role = extractRole(userDetails);
        OrderResponse order = orderService.sendToWarehouse(id, userId, role);
        return ResponseEntity.ok(ApiResponse.success("Đơn đã được gửi qua Warehouse.", order));
    }

    /**
     * Mark order as prepared — PROCESSING → SHIPPING.
     * Convenience endpoint with clear business semantics.
     * Role: WAREHOUSE or ADMIN only.
     */
    @PostMapping("/{id}/mark-prepared")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Mark order prepared (PROCESSING → SHIPPING)",
               description = "Warehouse marks the order as packed and ready to ship. " +
                       "Returns descriptive error if called by a non-Warehouse role or if order is not PROCESSING.")
    public ResponseEntity<ApiResponse<OrderResponse>> markPrepared(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        String userId = userDetails.getUsername();
        String role = extractRole(userDetails);
        OrderResponse order = orderService.markPrepared(id, userId, role);
        return ResponseEntity.ok(ApiResponse.success("Đơn đã được đánh dấu chuẩn bị xong.", order));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Cancel order",
               description = "Customer can cancel PENDING/CONFIRMED orders. ADMIN can cancel any order.")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {

        String userId = userDetails.getUsername();
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(((SimpleGrantedAuthority) a).getAuthority()));

        OrderResponse order = orderService.cancelOrder(id, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled", order));
    }

    /**
     * Force-cancel an order that already left PENDING/CONFIRMED
     * (i.e. SHIPPING or DELIVERED) because it carried a promotion code.
     * The partial-receipt flow was previously the only cancellation path
     * for those states; this endpoint gives ADMIN a way to fully void the
     * order and refund the customer without any product-receipt paperwork.
     *
     * Use case: the campaign rules force "all-or-nothing" delivery, so the
     * only legitimate outcomes for a promo order in flight are "delivered
     * in full" or "cancelled". This admin endpoint implements the latter.
     */
    @PostMapping("/{id}/force-cancel-promo")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin force-cancel a SHIPPING/DELIVERED order (promo only)",
               description = "ADMIN-only. Only allowed when the order has a promotion code AND its current " +
                       "status is SHIPPING or DELIVERED. Releases inventory if SHIPPING, marks CANCELLED + " +
                       "REFUNDED, notifies the customer.")
    public ResponseEntity<ApiResponse<OrderResponse>> forceCancelPromo(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @RequestBody(required = false) java.util.Map<String, String> body) {

        String userId = userDetails.getUsername();
        String reason = body != null ? body.get("reason") : null;
        OrderResponse order = orderService.forceCancelWithPromotion(id, userId, reason);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã hủy đơn hàng có khuyến mãi và hoàn tiền cho khách.", order));
    }

    /**
     * Append a tracking event to a SHIPPING order.
     * When {@code isDelivered=true} is passed, also triggers the
     * SHIPPING → DELIVERED transition in the same call.
     *
     * Role: SALES, WAREHOUSE, ADMIN.
     */
    @PostMapping("/{id}/tracking-updates")
    @PreAuthorize("hasAnyRole('SALES', 'WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Add a tracking update to a SHIPPING order",
            description = "Appends a tracking entry. If isDelivered=true, also transitions " +
                    "SHIPPING → DELIVERED (commits inventory, pushes statusHistory, notifies customer). " +
                    "If isDelivered=false (or omitted), only appends the tracking entry and notifies " +
                    "the customer — order status stays SHIPPING.")
    public ResponseEntity<ApiResponse<OrderResponse>> addTrackingUpdate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @Valid @RequestBody TrackingUpdateRequest request) {

        String userId = userDetails.getUsername();
        OrderResponse order = orderService.addTrackingUpdate(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(
                Boolean.TRUE.equals(request.getIsDelivered())
                        ? "Đã ghi nhận tracking và chuyển trạng thái sang DELIVERED."
                        : "Đã thêm tracking update.",
                order));
    }

    /**
     * Read the shipment timeline for a single order.
     * Owner (customer who placed the order) or SALES/WAREHOUSE/ADMIN can read.
     */
    @GetMapping("/{id}/tracking-updates")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SALES', 'WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Get the tracking-updates timeline of an order",
            description = "Returns the list of tracking entries appended while the order was in SHIPPING. " +
                    "Accessible by the order owner (customer) or any staff role.")
    public ResponseEntity<ApiResponse<List<TrackingUpdate>>> getTrackingUpdates(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {

        String userId = userDetails.getUsername();
        boolean isStaff = userDetails.getAuthorities().stream()
                .anyMatch(a -> {
                    String auth = ((SimpleGrantedAuthority) a).getAuthority();
                    return "ROLE_ADMIN".equals(auth)
                            || "ROLE_SALES".equals(auth)
                            || "ROLE_WAREHOUSE".equals(auth);
                });

        // Service enforces ownership/staff rule for non-staff callers.
        List<TrackingUpdate> updates = orderService.getTrackingUpdatesForUser(id, userId, isStaff);
        return ResponseEntity.ok(ApiResponse.success(updates));
    }

    /**
     * Nhận lại hàng từ nhân viên giao hàng (NVGH).
     *
     * Business rules:
     * - Only for orders in SHIPPING status
     * - Role: SALES or ADMIN only (not WAREHOUSE, because WAREHOUSE is the delivery role)
     * - Two scenarios:
     *   1. All items have receivedQuantity = 0 → order becomes CANCELLED
     *   2. Any item has receivedQuantity > 0 → order becomes DELIVERED with adjusted quantities
     *
     * Side effects:
     * - Updates subtotalAmount and totalAmount based on received quantities
     * - Releases inventory for rejected items back to stock
     * - Commits inventory for accepted items
     * - Adds status history entry with note
     */
    @PostMapping("/{id}/receive-return")
    @PreAuthorize("hasAnyRole('SALES', 'ADMIN')")
    @Operation(summary = "Nhận lại hàng từ NVGH (SHIPPING → DELIVERED or CANCELLED)",
            description = "When the delivery person returns undelivered items, " +
                    "Sales/Admin records what the customer actually received. " +
                    "If all quantities are 0 → CANCELLED. Otherwise → DELIVERED with adjusted totals.")
    public ResponseEntity<ApiResponse<OrderResponse>> receiveReturn(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @Valid @RequestBody ReceiveReturnRequest request) {

        String userId = userDetails.getUsername();
        OrderResponse order = orderService.receiveReturn(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận nhận lại hàng.", order));
    }
}
