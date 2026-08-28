package com.woodfurni.promotion.controller;

import com.woodfurni.common.ApiResponse;
import com.woodfurni.promotion.dto.PromotionRequest;
import com.woodfurni.promotion.dto.PromotionResponse;
import com.woodfurni.promotion.dto.ValidatePromotionRequest;
import com.woodfurni.promotion.dto.ValidatePromotionResponse;
import com.woodfurni.promotion.enums.PromotionStatus;
import com.woodfurni.promotion.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/promotions")
@RequiredArgsConstructor
@Tag(name = "Promotions", description = "Promotion / voucher management")
public class PromotionController {

    private final PromotionService promotionService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all promotions", description = "ADMIN only", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<PromotionResponse>>> getAll() {
        List<PromotionResponse> promotions = promotionService.getAll();
        return ResponseEntity.ok(ApiResponse.success(promotions));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get promotion by ID", description = "ADMIN only", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PromotionResponse>> getById(@PathVariable String id) {
        PromotionResponse promotion = promotionService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(promotion));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new promotion", description = "ADMIN only", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PromotionResponse>> create(
            @Valid @RequestBody PromotionRequest request) {
        PromotionResponse created = promotionService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Promotion created successfully", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a promotion", description = "ADMIN only", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PromotionResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody PromotionRequest request) {
        PromotionResponse updated = promotionService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Promotion updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a promotion", description = "ADMIN only", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        promotionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Promotion deleted successfully"));
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Validate a promotion code and calculate discount",
               description = "Read-only check. Returns valid=true with discountAmount if code is applicable. " +
                       "Does NOT increment usage count. Supports CUSTOMER and ADMIN roles.")
    public ResponseEntity<ApiResponse<ValidatePromotionResponse>> validate(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ValidatePromotionRequest request) {
        ValidatePromotionResponse result = promotionService.validateAndCalculate(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
