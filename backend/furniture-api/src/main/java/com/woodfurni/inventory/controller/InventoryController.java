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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.nio.file.Path;

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

    /**
     * Serve a previously uploaded evidence file for download.
     * URL pattern: GET /api/inventory/evidence/{yearMonth}/{filename}
     *
     * The original filename is resolved from the DB history record so that
     * the user downloads the file with the name they uploaded it as (e.g.
     * "phieu-dieu-chinh-2026-09.xlsx") instead of the UUID we stored on disk.
     */
    @GetMapping("/evidence/{yearMonth}/{filename:.+}")
    @PreAuthorize("hasAnyRole('WAREHOUSE', 'ADMIN')")
    @Operation(summary = "Download inventory adjustment evidence file",
               description = "Serves the Excel evidence file that was uploaded with an adjustment.")
    public ResponseEntity<Resource> downloadEvidence(
            @PathVariable String yearMonth,
            @PathVariable String filename) {
        String publicPath = "/api/inventory/evidence/" + yearMonth + "/" + filename;
        Path absolute = evidenceStorageService.resolve(publicPath);

        if (absolute == null) {
            log.warn("[downloadEvidence] File not found on disk: {}", publicPath);
            return ResponseEntity.notFound().build();
        }

        Resource resource = null;
        try {
            // Look up the history record to get the user-friendly original name.
            String originalName = inventoryService.findOriginalNameByStoredFile(filename)
                    .orElse(filename);

            // Sanitise for Content-Disposition header (ASCII fallback for non-ASCII).
            String safeAscii = originalName.replaceAll("[^\\x20-\\x7E]", "_");
            String contentDisposition =
                    "attachment; filename=\"" + safeAscii + "\"; "
                  + "filename*=UTF-8''" + java.net.URLEncoder.encode(originalName, java.nio.charset.StandardCharsets.UTF_8);

            // Pick content type by the actual on-disk extension (filename param
            // is the UUID we stored, so use that for the sniff).
            String lower = filename.toLowerCase();
            MediaType contentType = lower.endsWith(".xls")
                    ? MediaType.parseMediaType("application/vnd.ms-excel")
                    : MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

            resource = new FileSystemResource(absolute);
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .body(resource);
        } catch (Exception ex) {
            log.error("[downloadEvidence] Failed to serve evidence file: path={} originalFilename={}",
                    absolute, filename, ex);
            if (resource != null && resource.isReadable()) {
                try { resource.getInputStream().close(); } catch (IOException ignored) {}
            }
            return ResponseEntity.internalServerError().build();
        }
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
