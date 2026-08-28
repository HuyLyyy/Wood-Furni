package com.woodfurni.catalog.product.controller;

import com.woodfurni.catalog.product.dto.*;
import com.woodfurni.catalog.product.enums.ProductEnvironment;
import com.woodfurni.catalog.product.enums.ProductRoom;
import com.woodfurni.catalog.product.enums.ProductStatus;
import com.woodfurni.catalog.product.service.ProductService;
import com.woodfurni.common.ApiResponse;
import com.woodfurni.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog management")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Search and filter products",
               description = "Supports keyword (text search), category, environment, room, woodType, price range, sorting, and pagination")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) ProductEnvironment environment,
            @RequestParam(required = false) ProductRoom room,
            @RequestParam(required = false, name = "woodType") String woodType,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        boolean isStaff = isStaff(authentication);

        ProductSearchRequest request = ProductSearchRequest.builder()
                .keyword(keyword)
                .category(category)
                .environment(environment)
                .room(room)
                .woodType(woodType)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .sort(sort)
                .build();

        PageResponse<ProductResponse> result = productService.searchProducts(request, page, size, isStaff);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable String id) {
        ProductResponse product = productService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get product by slug")
    public ResponseEntity<ApiResponse<ProductResponse>> getBySlug(@PathVariable String slug) {
        ProductResponse product = productService.getBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CONTENT', 'ADMIN')")
    @Operation(summary = "Create a new product", description = "Creates with status=DRAFT by default", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Product created successfully", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTENT', 'ADMIN')")
    @Operation(summary = "Update a product", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody ProductRequest request) {
        ProductResponse updated = productService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", updated));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('CONTENT', 'ADMIN')")
    @Operation(summary = "Change product status (e.g., publish/unpublish)",
               description = "Publishing to ACTIVE requires at least one image", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ProductResponse>> changeStatus(
            @PathVariable String id,
            @Valid @RequestBody ProductStatusRequest request) {
        ProductResponse updated = productService.changeStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Product status updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a product", description = "ADMIN only", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully"));
    }

    private boolean isStaff(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_CONTENT"));
    }
}
