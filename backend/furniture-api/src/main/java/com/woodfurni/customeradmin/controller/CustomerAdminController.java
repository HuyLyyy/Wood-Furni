package com.woodfurni.customeradmin.controller;

import com.woodfurni.common.ApiResponse;
import com.woodfurni.common.PageResponse;
import com.woodfurni.customeradmin.dto.CustomerAdminView;
import com.woodfurni.customeradmin.dto.CustomerDetailView;
import com.woodfurni.customeradmin.service.CustomerAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin endpoints for browsing customers. Backend enforces role at method
 * level — SALES can also read this to follow up with high-value buyers.
 *
 *   GET /api/v1/admin/customers                   → paged list
 *   GET /api/v1/admin/customers/{id}              → detail + order history
 */
@RestController
@RequestMapping("/admin/customers")
@RequiredArgsConstructor
@Tag(name = "Admin Customers", description = "Customer browse endpoints")
@SecurityRequirement(name = "bearerAuth")
public class CustomerAdminController {

    private final CustomerAdminService customerAdminService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
    @Operation(summary = "List customers (CUSTOMER role only) with order metrics")
    public ResponseEntity<ApiResponse<PageResponse<CustomerAdminView>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<CustomerAdminView> items = customerAdminService.listCustomers(page, size);
        long total = customerAdminService.countCustomers();
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);

        PageResponse<CustomerAdminView> body = PageResponse.<CustomerAdminView>builder()
                .items(items)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
    @Operation(summary = "Customer detail with order history")
    public ResponseEntity<ApiResponse<CustomerDetailView>> detail(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(customerAdminService.getCustomerDetail(id)));
    }
}
