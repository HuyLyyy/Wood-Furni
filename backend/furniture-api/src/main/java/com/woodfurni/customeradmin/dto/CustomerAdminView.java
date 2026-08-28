package com.woodfurni.customeradmin.dto;

import com.woodfurni.auth.enums.Role;
import com.woodfurni.auth.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * CustomerAdminView — user summary enriched with metrics for the admin
 * customer list. Excludes sensitive fields (passwordHash, tokens).
 *
 * `orderCount` is computed via a single MongoDB $lookup + $size from the
 * order repository so the table can render without an N+1 round-trip.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAdminView {
    private String id;
    private String email;
    private String fullName;
    private String phone;
    private Role role;
    private UserStatus status;
    private Instant createdAt;
    /** Total number of orders placed by this customer (any status). */
    private long orderCount;
    /** Sum of totalAmount for PAID orders (DELIVERED included). */
    private java.math.BigDecimal totalSpent;
}
