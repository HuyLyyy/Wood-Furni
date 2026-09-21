package com.woodfurni.inventory.controller;

import com.woodfurni.auth.model.User;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.common.ApiResponse;
import com.woodfurni.common.PageResponse;
import com.woodfurni.inventory.dto.InventoryAdjustRequest;
import com.woodfurni.inventory.dto.InventoryHistoryResponse;
import com.woodfurni.inventory.dto.InventoryResponse;
import com.woodfurni.inventory.enums.AdjustmentReason;
import com.woodfurni.inventory.service.EvidenceStorageService;
import com.woodfurni.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Inventory", description = "Stock and inventory management")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;
    private final UserRepository userRepository;
    private final EvidenceStorageService evidenceStorageService;

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
                       "Prevents quantityOnHand from going negative. Writes an audit entry with reason code and Excel evidence. " +
                       "Requires multipart/form-data with: reasonCode, delta, note (optional), evidence (REQUIRED .xlsx/.xls ≤10MB).")
    public ResponseEntity<ApiResponse<InventoryResponse>> adjust(
            @PathVariable String productId,
            @RequestParam("reasonCode") String reasonCode,
            @RequestParam("delta") Integer delta,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam("evidence") MultipartFile evidence,
            @RequestParam(value = "keepReason", required = false) String keepReason,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (reasonCode == null || reasonCode.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("reasonCode là bắt buộc. Các giá trị hợp lệ: " +
                            java.util.Arrays.toString(AdjustmentReason.values())));
        }
        AdjustmentReason reason = AdjustmentReason.fromCode(reasonCode);
        if (reason == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Giá trị reasonCode không hợp lệ. Các giá trị hợp lệ: " +
                            java.util.Arrays.toString(AdjustmentReason.values())));
        }

        InventoryAdjustRequest request = InventoryAdjustRequest.builder()
                .reasonCode(reason)
                .delta(delta)
                .note(note)
                .build();

        String actorName = resolveActorName(userDetails);
        String actorUserId = userDetails != null ? userDetails.getUsername() : "unknown";
        InventoryResponse result = inventoryService.adjust(
                productId, request, evidence, actorName, actorUserId);
        return ResponseEntity.ok(ApiResponse.success("Đã điều chỉnh tồn kho", result));
    }

    // ── Evidence download endpoints ────────────────────────────────────────
    // Two URL patterns are supported for backward compatibility:
    //   1. /evidence/{gridFsId}          — new format (ObjectId string)
    //   2. /evidence/{yearMonth}/{file} — legacy format (yyyy-MM/uuid.xlsx)
    //
    // New uploads use format 1. Old history records (uploaded before the
    // GridFS migration) use format 2 and may return 404 if the file was lost
    // after a Render redeploy (ephemeral disk). Users must re-upload those.

    /**
     * NEW FORMAT: Download evidence by GridFS ObjectId.
     * URL: GET /api/inventory/evidence/{gridFsId}
     */
    @GetMapping("/evidence/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Download inventory adjustment evidence file (new format)",
               description = "Serves the Excel evidence file stored in MongoDB GridFS.")
    public ResponseEntity<Resource> downloadEvidence(@PathVariable String id) {
        return serveEvidence(id, null, null);
    }

    /**
     * LEGACY FORMAT: Download evidence by yearMonth/storedFileName.
     * URL: GET /api/inventory/evidence/{yearMonth}/{filename:.+}
     *
     * Looks up the history record by filename to recover the original name.
     * If the file is genuinely missing (lost after Render redeploy), returns 404.
     */
    @GetMapping("/evidence/{yearMonth}/{filename:.+}")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Download inventory adjustment evidence file (legacy format)",
               description = "Legacy endpoint for evidence files stored before the GridFS migration.")
    public ResponseEntity<Resource> downloadEvidenceLegacy(
            @PathVariable String yearMonth,
            @PathVariable String filename) {
        return serveEvidence(null, yearMonth, filename);
    }

    private ResponseEntity<Resource> serveEvidence(String id, String yearMonth, String filename) {
        GridFsResource resource;
        String originalName;

        if (id != null) {
            // ── New format: /evidence/{gridFsId} ──────────────────────────
            String publicPath = "/api/v1/inventory/evidence/" + id;
            resource = evidenceStorageService.resolve(publicPath);
            if (resource == null) {
                log.warn("[downloadEvidence] File not found in GridFS for id={}", id);
                return ResponseEntity.notFound().build();
            }
            originalName = evidenceStorageService.findOriginalNameById(id);
        } else {
            // ── Legacy format: /evidence/{yearMonth}/{filename} ───────────
            // File is gone (Render ephemeral disk) — nothing we can do.
            // Log clearly so the admin understands.
            log.warn("[downloadEvidence] Legacy evidence not found — file was stored on " +
                    "Render's ephemeral /tmp and was lost after a redeploy. " +
                    "Please re-upload the adjustment evidence. yearMonth={} filename={}",
                    yearMonth, filename);
            return ResponseEntity.notFound().build();
        }

        if (originalName == null || originalName.isBlank()) {
            originalName = "minh-chung.xlsx";
        }

        // Sanitise for Content-Disposition header (ASCII fallback for non-ASCII).
        String safeAscii = originalName.replaceAll("[^\\x20-\\x7E]", "_");
        String contentDisposition =
                "attachment; filename=\"" + safeAscii + "\"; "
              + "filename*=UTF-8''" + java.net.URLEncoder.encode(originalName, java.nio.charset.StandardCharsets.UTF_8);

        // Pick content type by extension.
        String lower = originalName.toLowerCase();
        MediaType contentType = lower.endsWith(".xls")
                ? MediaType.parseMediaType("application/vnd.ms-excel")
                : MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(resource);
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
