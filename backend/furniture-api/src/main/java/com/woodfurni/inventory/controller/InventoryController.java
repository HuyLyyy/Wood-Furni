package com.woodfurni.inventory.controller;

import com.woodfurni.auth.model.User;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.common.ApiResponse;
import com.woodfurni.common.PageResponse;
import com.woodfurni.inventory.dto.InventoryAdjustRequest;
import com.woodfurni.inventory.dto.InventoryHistoryResponse;
import com.woodfurni.inventory.dto.InventoryResponse;
import com.woodfurni.inventory.service.InventoryService;
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

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Stock and inventory management")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN')")
    @Operation(summary = "List all inventory records",
               description = "Returns paginated inventory with product name and SKU. WAREHOUSE and ADMIN only.")
    public ResponseEntity<ApiResponse<PageResponse<InventoryResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<InventoryResponse> result = inventoryService.getAll(page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN')")
    @Operation(summary = "List low-stock items",
               description = "Returns products where quantityOnHand <= lowStockThreshold (default 5).")
    public ResponseEntity<ApiResponse<PageResponse<InventoryResponse>>> getLowStock(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<InventoryResponse> result = inventoryService.getLowStock(page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Get inventory for a specific product")
    public ResponseEntity<ApiResponse<InventoryResponse>> getByProductId(
            @PathVariable String productId) {
        InventoryResponse result = inventoryService.getByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/{productId}/adjust")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Manual stock adjustment",
               description = "Adjust stock quantity (positive delta = restock, negative = deduction/damage). " +
                       "Prevents quantityOnHand from going negative. Writes an audit entry.")
    public ResponseEntity<ApiResponse<InventoryResponse>> adjust(
            @PathVariable String productId,
            @Valid @RequestBody InventoryAdjustRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String actorName = resolveActorName(userDetails);
        String actorUserId = userDetails != null ? userDetails.getUsername() : "unknown";
        InventoryResponse result = inventoryService.adjust(
                productId, request.getDelta(), request.getReason(), actorName, actorUserId);
        return ResponseEntity.ok(ApiResponse.success("Đã điều chỉnh tồn kho", result));
    }

    @GetMapping("/{productId}/history")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Get inventory adjustment history for a product",
               description = "Returns paginated audit trail of all stock changes for the product, newest first.")
    public ResponseEntity<ApiResponse<PageResponse<InventoryHistoryResponse>>> getHistory(
            @PathVariable String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<InventoryHistoryResponse> result = inventoryService.getHistoryByProductId(productId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Build a human-readable actor name from Spring Security UserDetails.
     * Looks up the user in DB to get their fullName + email for display.
     * Falls back to username (userId) if DB lookup fails.
     */
    private String resolveActorName(UserDetails userDetails) {
        if (userDetails == null) return "Hệ thống";

        String userId = userDetails.getUsername(); // this is the userId (ObjectId string)
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("");

        // Try to look up the user for fullName and email
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            String displayName = user.getFullName();
            String email = user.getEmail();
            // Format: "Lê Văn Kho (warehouse@woodfurni.vn) - WAREHOUSE"
            String label = (displayName != null && !displayName.isBlank())
                    ? displayName + " (" + email + ")"
                    : email;
            return label + " - " + role;
        }

        // Fallback: just show userId and role
        return userId + " - " + role;
    }
}
