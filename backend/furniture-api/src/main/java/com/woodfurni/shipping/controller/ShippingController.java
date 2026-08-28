package com.woodfurni.shipping.controller;

import com.woodfurni.common.ApiResponse;
import com.woodfurni.shipping.dto.DistrictResponse;
import com.woodfurni.shipping.dto.ShippingCalculateRequest;
import com.woodfurni.shipping.dto.ShippingCalculateResponse;
import com.woodfurni.shipping.dto.ShippingDistanceRequest;
import com.woodfurni.shipping.dto.ShippingDistanceResponse;
import com.woodfurni.shipping.model.ShippingDistance;
import com.woodfurni.shipping.service.HcmAddressService;
import com.woodfurni.shipping.service.ShippingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Shipping controller.
 *
 * Endpoints:
 *
 *   GET  /shipping/distances           — public  (list all HCM districts for checkout form)
 *   POST /shipping/distances           — ADMIN  (add a new distance record)
 *   PUT  /shipping/distances/{id}     — ADMIN  (update a distance record)
 *   POST /shipping/calculate          — CUSTOMER (preview fee for a delivery address)
 *   GET  /shipping/districts           — public  (HCM district + ward tree for address picker)
 *   GET  /shipping/districts/{name}/wards — public (wards for one district)
 */
@RestController
@RequestMapping("/shipping")
@RequiredArgsConstructor
@Tag(name = "Shipping", description = "Shipping distance table and fee calculation")
@SecurityRequirement(name = "bearerAuth")
public class ShippingController {

    private final ShippingService shippingService;
    private final HcmAddressService hcmAddressService;

    // ========================================================================
    // Public — list all distance records (for checkout address form)
    // ========================================================================

    @GetMapping("/distances")
    @Operation(summary = "List all distance records (public)",
               description = "Returns every city/district pair in the distance table. " +
                       "Use this to populate the address dropdown on the checkout page.")
    public ResponseEntity<ApiResponse<List<ShippingDistanceResponse>>> listDistances() {
        List<ShippingDistanceResponse> distances = shippingService.getAllDistances().stream()
                .map(ShippingDistanceResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(distances));
    }

    // ========================================================================
    // Admin — distance record CRUD
    // ========================================================================

    @PostMapping("/distances")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add a new distance record (ADMIN)",
               description = "Creates a (city, district) → distanceKm mapping. " +
                       "The unique compound index prevents duplicate pairs.")
    public ResponseEntity<ApiResponse<ShippingDistanceResponse>> createDistance(
            @Valid @RequestBody ShippingDistanceRequest request) {
        ShippingDistance created = shippingService.createDistance(request);
        return ResponseEntity.ok(
                ApiResponse.success("Khu vực đã được thêm", ShippingDistanceResponse.fromEntity(created)));
    }

    @PutMapping("/distances/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a distance record (ADMIN)",
               description = "Updates the distanceKm value for an existing (city, district) pair.")
    public ResponseEntity<ApiResponse<ShippingDistanceResponse>> updateDistance(
            @PathVariable String id,
            @Valid @RequestBody ShippingDistanceRequest request) {
        ShippingDistance updated = shippingService.updateDistance(id, request);
        return ResponseEntity.ok(
                ApiResponse.success("Khu vực đã được cập nhật", ShippingDistanceResponse.fromEntity(updated)));
    }

    // ========================================================================
    // Customer — fee preview at checkout
    // ========================================================================

    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Preview shipping fee (CUSTOMER)",
               description = "Computes the shipping fee for a delivery address without creating an order. " +
                       "Call this from the checkout page as soon as the customer finishes entering their " +
                       "city/district so they see the fee before placing the order.")
    public ResponseEntity<ApiResponse<ShippingCalculateResponse>> calculate(
            @Valid @RequestBody ShippingCalculateRequest request) {
        ShippingCalculateResponse result = shippingService.calculateFee(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ========================================================================
    // Public — HCM district/ward reference for the address picker
    // ========================================================================

    @GetMapping("/districts")
    @Operation(summary = "List HCM districts + wards (public)",
               description = "Returns the full tree of Hồ Chí Minh districts and the wards under each. " +
                       "Loaded from resources/shipping/hcm-districts.json. Used to populate the " +
                       "checklist-style address picker on the checkout page.")
    public ResponseEntity<ApiResponse<DistrictResponse>> listDistricts() {
        return ResponseEntity.ok(ApiResponse.success(hcmAddressService.listAll()));
    }

    @GetMapping("/districts/{name}/wards")
    @Operation(summary = "List wards for one district (public)",
               description = "Returns just the ward list for the requested district name. Used by the " +
                       "second step of the address picker once the customer has chosen a district.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listWards(@PathVariable("name") String name) {
        Optional<List<String>> wards = hcmAddressService.findWards(name);
        Map<String, Object> payload = Map.of(
                "district", name,
                "wards", wards.orElse(Collections.emptyList())
        );
        return ResponseEntity.ok(ApiResponse.success(payload));
    }
}
