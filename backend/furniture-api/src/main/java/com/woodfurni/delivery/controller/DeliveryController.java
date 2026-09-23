package com.woodfurni.delivery.controller;

import com.woodfurni.common.ApiResponse;
import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.common.PageResponse;
import com.woodfurni.delivery.dto.*;
import com.woodfurni.delivery.enums.VehicleType;
import com.woodfurni.delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller cho module Chuyến xe — base path: /delivery.
 *
 * Endpoints:
 *   GET  /delivery/vehicle-types                — list các loại xe (capacity table)
 *   GET  /delivery/eligible-orders              — đơn SHIPPING chưa gán trip
 *   POST /delivery/trips/preview                — xem trước capacity
 *   POST /delivery/trips                        — tạo chuyến xe
 *   GET  /delivery/trips                        — list chuyến (search/filter)
 *   GET  /delivery/trips/{id}                   — chi tiết chuyến + danh sách đơn
 *
 * Role: WAREHOUSE / ADMIN (theo yêu cầu nghiệp vụ — SALES cũng có thể xem).
 */
@RestController
@RequestMapping("/delivery")
@RequiredArgsConstructor
@Tag(name = "Delivery", description = "Quản lý chuyến xe giao hàng")
@SecurityRequirement(name = "bearerAuth")
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping("/vehicle-types")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'SALES')")
    @Operation(summary = "List các loại xe vận chuyển + capacity")
    public ResponseEntity<ApiResponse<List<VehicleTypeResponse>>> listVehicleTypes() {
        List<VehicleTypeResponse> list = Arrays.stream(VehicleType.values())
                .map(VehicleTypeResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/eligible-orders")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'SALES')")
    @Operation(summary = "Danh sách đơn hàng ở trạng thái SHIPPING — đã chuẩn bị xong — chưa gán vào chuyến nào")
    public ResponseEntity<ApiResponse<PageResponse<EligibleOrderResponse>>> listEligibleOrders(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<EligibleOrderResponse> result = deliveryService.listEligibleOrders(search, page, size);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(result)));
    }

    @PostMapping("/trips/preview")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'SALES')")
    @Operation(summary = "Xem trước capacity: tổng các đơn đã chọn có lọt vào loại xe đã chọn không")
    public ResponseEntity<ApiResponse<TripCapacityResponse>> previewCapacity(
            @Valid @RequestBody TripCapacityRequest request) {
        TripCapacityResponse result = deliveryService.previewCapacity(request.getOrderIds(), request.getVehicleTypeCode());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/trips")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Tạo chuyến xe mới (validate capacity trước khi ghi)")
    public ResponseEntity<ApiResponse<DeliveryTripResponse>> createTrip(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateDeliveryTripRequest request) {
        String createdBy = userDetails != null ? userDetails.getUsername() : "system";
        DeliveryTripResponse trip = deliveryService.createTrip(
                request.getOrderIds(),
                request.getVehicleTypeCode(),
                request.getDriverId(),
                request.getAssemblerIds(),
                request.getNote(),
                createdBy);
        return ResponseEntity.ok(ApiResponse.success("Đã tạo chuyến xe.", trip));
    }

    @GetMapping("/trips")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'SALES')")
    @Operation(summary = "Danh sách chuyến xe (search theo tripNumber hoặc filter theo status)")
    public ResponseEntity<ApiResponse<PageResponse<DeliveryTripResponse>>> listTrips(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DeliveryTripResponse> result = deliveryService.listTrips(status, search, page, size);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(result)));
    }

    @GetMapping("/trips/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'SALES')")
    @Operation(summary = "Chi tiết chuyến xe — kèm danh sách đơn đã gán")
    public ResponseEntity<ApiResponse<DeliveryTripResponse>> getTripDetail(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.getTripDetail(id)));
    }

    // ───────────────────────────────────────────────────────────────────────
    // TRIP ACTIONS
    // ───────────────────────────────────────────────────────────────────────

    @PostMapping("/trips/{id}/start")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'DRIVER')")
    @Operation(summary = "Bắt đầu giao hàng — chuyến chuyển sang ĐANG GIAO")
    public ResponseEntity<ApiResponse<DeliveryTripResponse>> startShipping(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        return ResponseEntity.ok(ApiResponse.success("Đã bắt đầu giao hàng.", deliveryService.startShipping(id, performedBy)));
    }

    @PostMapping("/trips/{id}/cancel")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Hủy chuyến xe — chuyến chuyển sang ĐÃ HỦY")
    public ResponseEntity<ApiResponse<DeliveryTripResponse>> cancelTrip(
            @PathVariable String id,
            @RequestBody(required = false) CancelTripRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(ApiResponse.success("Đã hủy chuyến xe.", deliveryService.cancelTrip(id, reason, performedBy)));
    }

    @PostMapping("/trips/{id}/complete")
    @PreAuthorize("hasAnyRole('SALES', 'ADMIN')")
    @Operation(summary = "Hoàn thành chuyến xe — chuyến chuyển sang HOÀN THÀNH (Sales/Admin xác nhận)")
    public ResponseEntity<ApiResponse<DeliveryTripResponse>> completeTrip(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        return ResponseEntity.ok(ApiResponse.success("Đã hoàn thành chuyến xe.", deliveryService.completeTrip(id, performedBy)));
    }
}
