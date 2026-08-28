package com.woodfurni.customeradmin.dto;

import com.woodfurni.auth.enums.Role;
import com.woodfurni.auth.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Detail view for a single customer — user fields + their order history.
 * Returned by GET /api/v1/admin/customers/{id}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDetailView {
    private String id;
    private String email;
    private String fullName;
    private String phone;
    private Role role;
    private UserStatus status;
    private long orderCount;
    private java.math.BigDecimal totalSpent;
    private List<CustomerOrderSummary> orders;
}
