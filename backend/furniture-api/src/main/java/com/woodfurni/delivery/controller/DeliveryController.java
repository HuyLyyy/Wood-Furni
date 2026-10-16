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

    /**
     * Tính lại status của 1 chuyến dựa trên per-order state. Dùng để sửa dữ liệu
     * cũ bị finalize logic sai.
     */
    @PostMapping("/trips/{id}/recompute-status")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'SALES')")
    @Operation(summary = "Tính lại trạng thái chuyến từ per-order state (fix dữ liệu cũ)")
    public ResponseEntity<ApiResponse<DeliveryTripResponse>> recomputeTripStatus(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        return ResponseEntity.ok(ApiResponse.success(
                "Đã tính lại trạng thái chuyến.",
                deliveryService.recomputeTripStatus(id, performedBy)));
    }

    /**
     * Dọn link orphan (đơn thuộc nhiều trip cùng lúc do bug cũ).
     * Admin only. Trả về số link đã xóa.
     */
    @PostMapping("/trips/cleanup-orphan-links")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Dọn link PENDING/RESCHEDULED orphan (đơn thuộc nhiều trip)")
    public ResponseEntity<ApiResponse<Integer>> cleanupOrphanLinks(
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        int removed = deliveryService.cleanupOrphanLinks(performedBy);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã dọn " + removed + " link orphan.", removed));
    }

    /**
     * Tính lại status cho TẤT CẢ chuyến (chỉ chạy 1 lần sau khi deploy fix).
     */
    @PostMapping("/trips/recompute-all")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Tính lại trạng thái cho tất cả chuyến (chỉ Admin)")
    public ResponseEntity<ApiResponse<Integer>> recomputeAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        int changed = deliveryService.recomputeAllTripStatuses(performedBy);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã tính lại: " + changed + " chuyến thay đổi trạng thái.", changed));
    }

    /**
     * Sửa dữ liệu nextDeliveryDate bị corrupted (Instant < 2025 do bug
     * timezone cũ). Chỉ Admin.
     */
    @PostMapping("/trips/fix-corrupted-dates")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Sửa nextDeliveryDate bị corrupted (chỉ Admin)")
    public ResponseEntity<ApiResponse<Integer>> fixCorruptedDates(
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        int fixed = deliveryService.fixCorruptedNextDeliveryDates(performedBy);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã sửa " + fixed + " dòng dữ liệu bị lỗi.", fixed));
    }

    /**
     * Debug: trả về raw nextDeliveryDate của tất cả orders + trips đang có
     * giá trị corrupted (< 2025). Dùng để kiểm tra nhanh không cần query DB.
     */
    @GetMapping("/trips/audit-corrupted-dates")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE', 'SALES')")
    @Operation(summary = "Audit các dòng nextDeliveryDate bị corrupted")
    public ResponseEntity<ApiResponse<Object>> auditCorruptedDates() {
        return ResponseEntity.ok(ApiResponse.success("Audit result",
                deliveryService.auditCorruptedNextDeliveryDates()));
    }

    // ───────────────────────────────────────────────────────────────────────
    // LUỒNG HOÃN GIAO HÀNG (Bước 1 → 9)
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Bước 1: Tài xế đã đến địa điểm giao hàng.
     * SHIPPING → ARRIVED.
     */
    @PostMapping("/trips/{id}/arrive")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'DRIVER')")
    @Operation(summary = "Tài xế đã đến địa điểm giao hàng — chuyến chuyển sang ĐÃ ĐẾN NƠI")
    public ResponseEntity<ApiResponse<DeliveryTripResponse>> arriveAtLocation(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        return ResponseEntity.ok(ApiResponse.success("Đã đánh dấu đến nơi.", deliveryService.arriveAtLocation(id, performedBy)));
    }

    /**
     * Bước 2A: Tài xế xác nhận giao thành công một đơn.
     */
    @PostMapping("/trips/{id}/orders/{orderId}/deliver")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'DRIVER')")
    @Operation(summary = "Xác nhận giao thành công một đơn trong chuyến — chuyển đơn sang DELIVERED")
    public ResponseEntity<ApiResponse<DeliveryActionResult>> markOrderDelivered(
            @PathVariable String id,
            @PathVariable String orderId,
            @Valid @RequestBody MarkOrderDeliveredRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        return ResponseEntity.ok(ApiResponse.success(
                "Đã xác nhận giao đơn thành công.",
                deliveryService.markOrderDelivered(id, orderId, request, performedBy)));
    }

    /**
     * Bước 2C: Tài xế không liên lạc được khách (sau 15-30 phút chờ).
     */
    @PostMapping("/trips/{id}/orders/{orderId}/unreachable")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'DRIVER')")
    @Operation(summary = "Đánh dấu không liên lạc được khách — chờ tài xế xác nhận cuối")
    public ResponseEntity<ApiResponse<DeliveryActionResult>> markOrderUnreachable(
            @PathVariable String id,
            @PathVariable String orderId,
            @RequestBody(required = false) MarkOrderUnreachableNote request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        String note = request != null ? request.getNote() : null;
        String photo = request != null ? request.getProofPhotoUrl() : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Đã đánh dấu không liên lạc được.",
                deliveryService.markOrderUnreachable(id, orderId, note, photo, performedBy)));
    }

    /**
     * Bước 2B → 9: Tài xế xác nhận HOÃN giao hàng (lý do + thời gian giao lại + ghi chú).
     * Đơn sẽ chuyển sang RESCHEDULED. Cảnh báo nếu đây là lần hoãn thứ 3.
     */
    @PostMapping("/trips/{id}/orders/{orderId}/reschedule")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'DRIVER')")
    @Operation(summary = "Xác nhận HOÃN giao một đơn — chuyển đơn sang RESCHEDULED với lý do và thời gian giao lại")
    public ResponseEntity<ApiResponse<DeliveryActionResult>> rescheduleOrder(
            @PathVariable String id,
            @PathVariable String orderId,
            @Valid @RequestBody RescheduleOrderRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        return ResponseEntity.ok(ApiResponse.success(
                "Đã ghi nhận hoãn giao đơn.",
                deliveryService.rescheduleOrder(id, orderId, request, performedBy)));
    }

    /**
     * Bước 4': Tài xế xác nhận giao THẤT BẠI (khách từ chối dứt khoát, địa chỉ
     * không tiếp cận được, ...). Đơn sẽ chuyển sang RETURNED.
     */
    @PostMapping("/trips/{id}/orders/{orderId}/fail")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'DRIVER')")
    @Operation(summary = "Xác nhận giao THẤT BẠI — chuyển đơn sang RETURNED (kết thúc, không giao lại)")
    public ResponseEntity<ApiResponse<DeliveryActionResult>> markOrderFailed(
            @PathVariable String id,
            @PathVariable String orderId,
            @Valid @RequestBody MarkOrderFailedRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        return ResponseEntity.ok(ApiResponse.success(
                "Đã đánh dấu giao thất bại.",
                deliveryService.markOrderFailed(id, orderId, request, performedBy)));
    }

    // ───────────────────────────────────────────────────────────────────────
    // RESCHEDULE FLOW (Tạo trip giao lại)
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Danh sách đơn hàng đang RESCHEDULED — chờ admin tạo trip giao lại.
     * Trả về thêm thông tin: số lần hoãn, lý do, nextDeliveryDate, source trip.
     */
    @GetMapping("/reschedule-orders")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'SALES')")
    @Operation(summary = "Danh sách đơn đang chờ giao lại (RESCHEDULED chưa tạo trip mới)")
    public ResponseEntity<ApiResponse<List<RescheduleOrderResponse>>> listRescheduleOrders() {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.listRescheduleOrders()));
    }

    /**
     * Tạo trip giao lại từ danh sách đơn RESCHEDULED.
     * - Unlink đơn khỏi trip cũ (giữ lịch sử).
     * - Link đơn vào trip mới với state PENDING.
     * - Order: RESCHEDULED → SHIPPING.
     */
    @PostMapping("/trips/reschedule")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Tạo chuyến xe giao lại từ các đơn đang RESCHEDULED")
    public ResponseEntity<ApiResponse<DeliveryTripResponse>> createRescheduleTrip(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateDeliveryTripRequest request) {
        String createdBy = userDetails != null ? userDetails.getUsername() : "system";
        DeliveryTripResponse trip = deliveryService.createRescheduleTrip(
                request.getOrderIds(),
                request.getVehicleTypeCode(),
                request.getDriverId(),
                request.getAssemblerIds(),
                request.getNote(),
                createdBy);
        return ResponseEntity.ok(ApiResponse.success("Đã tạo chuyến giao lại.", trip));
    }

    // ───────────────────────────────────────────────────────────────────────
    // BULK ACTIONS
    // ───────────────────────────────────────────────────────────────────────

    @PostMapping("/trips/bulk-start")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN', 'DRIVER')")
    @Operation(summary = "Bắt đầu giao nhiều chuyến cùng lúc — các chuyến chuyển sang ĐANG GIAO")
    public ResponseEntity<ApiResponse<BulkActionResponse>> bulkStartShipping(
            @RequestBody BulkTripActionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        BulkActionResponse result = deliveryService.bulkStartShipping(request.getTripIds(), performedBy);
        return ResponseEntity.ok(ApiResponse.success("Đã bắt đầu giao " + result.getSuccessCount() + " chuyến.", result));
    }

    @PostMapping("/trips/bulk-cancel")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Hủy nhiều chuyến xe cùng lúc — các chuyến chuyển sang ĐÃ HỦY")
    public ResponseEntity<ApiResponse<BulkActionResponse>> bulkCancelTrips(
            @RequestBody BulkCancelTripRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String performedBy = userDetails != null ? userDetails.getUsername() : "system";
        BulkActionResponse result = deliveryService.bulkCancelTrips(request.getTripIds(), request.getReason(), performedBy);
        return ResponseEntity.ok(ApiResponse.success("Đã hủy " + result.getSuccessCount() + " chuyến.", result));
    }
}
