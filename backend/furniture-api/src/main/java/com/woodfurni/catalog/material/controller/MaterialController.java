package com.woodfurni.catalog.material.controller;

import com.woodfurni.catalog.material.dto.MaterialRequest;
import com.woodfurni.catalog.material.dto.MaterialResponse;
import com.woodfurni.catalog.material.service.MaterialService;
import com.woodfurni.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/materials")
@RequiredArgsConstructor
@Tag(name = "Materials", description = "Product material management (wood types, finishes, etc.)")
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    @Operation(summary = "Get all materials", description = "Public endpoint for listing all materials")
    public ResponseEntity<ApiResponse<List<MaterialResponse>>> getAll() {
        List<MaterialResponse> materials = materialService.getAll();
        return ResponseEntity.ok(ApiResponse.success(materials));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get material by ID")
    public ResponseEntity<ApiResponse<MaterialResponse>> getById(@PathVariable String id) {
        MaterialResponse material = materialService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(material));
    }

    @GetMapping("/search")
    @Operation(summary = "Search materials by name")
    public ResponseEntity<ApiResponse<List<MaterialResponse>>> search(
            @RequestParam(required = false) String keyword) {
        List<MaterialResponse> materials = materialService.search(keyword);
        return ResponseEntity.ok(ApiResponse.success(materials));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CONTENT', 'ADMIN')")
    @Operation(summary = "Create a new material", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MaterialResponse>> create(
            @Valid @RequestBody MaterialRequest request) {
        MaterialResponse created = materialService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Material created successfully", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTENT', 'ADMIN')")
    @Operation(summary = "Update a material", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MaterialResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody MaterialRequest request) {
        MaterialResponse updated = materialService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Material updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTENT', 'ADMIN')")
    @Operation(summary = "Delete a material", description = "CONTENT and ADMIN only", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        materialService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Material deleted successfully"));
    }
}
