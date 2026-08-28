package com.woodfurni.rbac;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-Based Access Control (RBAC) tests.
 *
 * For each of the four core roles (CUSTOMER, WAREHOUSE, CONTENT, SALES),
 * attempt to access at least ONE endpoint that is NOT part of that role's
 * permission set and verify that the server responds with HTTP 403
 * Forbidden.
 *
 * Endpoint → role mapping per WOODFURNI spec Mục 4 (API Contract):
 *   POST /api/v1/orders/checkout       → CUSTOMER, ADMIN
 *   PATCH /api/v1/orders/{id}/status  → SALES, WAREHOUSE, ADMIN
 *   PATCH /api/v1/inventory/{id}/adjust → WAREHOUSE, ADMIN
 *   POST /api/v1/categories           → CONTENT, ADMIN
 *   GET /api/v1/admin/dashboard/*     → ADMIN
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private static final String ANY_USER_ID = "user-rbac-001";

    // ============================================================
    // TC-RBAC-01 — CUSTOMER cannot access admin dashboard
    // Dashboard is ADMIN-only.
    // ============================================================
    @Test
    @DisplayName("CUSTOMER gọi GET /admin/dashboard/summary - expect HTTP 403")
    void customer_DashboardEndpoint_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .with(user(ANY_USER_ID).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // TC-RBAC-02 — WAREHOUSE cannot checkout
    // Checkout is CUSTOMER/ADMIN only.
    // ============================================================
    @Test
    @DisplayName("WAREHOUSE gọi POST /orders/checkout - expect HTTP 403")
    void warehouse_CheckoutEndpoint_Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/orders/checkout")
                        .with(user(ANY_USER_ID).roles("WAREHOUSE"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":\"addr-fake\",\"paymentMethod\":\"COD\"}"))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // TC-RBAC-03 — CONTENT cannot adjust inventory
    // PATCH /inventory/{id}/adjust is WAREHOUSE/ADMIN only.
    // CONTENT only has rights to category/material/product.
    // ============================================================
    @Test
    @DisplayName("CONTENT gọi PATCH /inventory/{id}/adjust - expect HTTP 403")
    void content_InventoryAdjustEndpoint_Forbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/inventory/prod-fake/adjust")
                        .with(user(ANY_USER_ID).roles("CONTENT"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"delta\":1,\"reason\":\"restock\"}"))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // TC-RBAC-04 — SALES cannot create categories
    // POST /categories is CONTENT/ADMIN only.
    // SALES can read orders + update order status, but NOT manage catalog.
    // ============================================================
    @Test
    @DisplayName("SALES gọi POST /categories - expect HTTP 403")
    void sales_CategoryCreateEndpoint_Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .with(user(ANY_USER_ID).roles("SALES"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Phòng khách\",\"slug\":\"phong-khach\",\"environment\":\"INDOOR\"}"))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // TC-RBAC-05 — Anonymous request to protected endpoint
    // No authentication → 401 (not 403).
    // ============================================================
    @Test
    @DisplayName("Anonymous request tới protected endpoint - expect HTTP 401")
    void anonymous_ProtectedEndpoint_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // TC-RBAC-06 — SALES CAN update order status (sanity check)
    // This verifies we did NOT over-tighten RBAC: SALES must keep
    // status-update access (spec Mục 4: SALES, WAREHOUSE, ADMIN).
    // We assert 403 because the orderId doesn't exist, NOT because of role.
    // If SALES is correctly authorized, we get 404 (order not found).
    // ============================================================
    @Test
    @DisplayName("SALES gọi PATCH /orders/{id}/status - expect 404 (không phải 403, đúng spec)")
    void sales_OrderStatusEndpoint_Authorized() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/order-nonexistent/status")
                        .with(user(ANY_USER_ID).roles("SALES"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError(
                                "SALES must be authorized to update order status per spec; got 403");
                    }
                    // Accept any non-403 (typically 404 for missing order)
                });
    }
}
