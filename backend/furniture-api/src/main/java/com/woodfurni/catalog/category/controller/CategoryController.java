package com.woodfurni.catalog.category.controller;

import com.woodfurni.catalog.category.dto.CategoryRequest;
import com.woodfurni.catalog.category.dto.CategoryResponse;
import com.woodfurni.catalog.category.dto.CategoryTreeResponse;
import com.woodfurni.catalog.category.service.CategoryService;
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
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Product category management")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Get all categories as tree", description = "Returns categories in hierarchical tree structure")
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getAllAsTree() {
        List<CategoryTreeResponse> tree = categoryService.getAllAsTree();
        return ResponseEntity.ok(ApiResponse.success(tree));
    }

    @GetMapping("/flat")
    @Operation(summary = "Get all categories as flat list", description = "Returns all categories in a flat list")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllFlat() {
        List<CategoryResponse> categories = categoryService.getAll();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(@PathVariable String id) {
        CategoryResponse category = categoryService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CONTENT', 'ADMIN')")
    @Operation(summary = "Create a new category", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CategoryRequest request) {
        CategoryResponse created = categoryService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Category created successfully", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTENT', 'ADMIN')")
    @Operation(summary = "Update a category", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody CategoryRequest request) {
        CategoryResponse updated = categoryService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a category", description = "ADMIN only. Fails if category has children.", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully"));
    }
}
