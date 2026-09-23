package com.woodfurni.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thông tin nhân viên lắp ráp phụ trợ — trả về từ GET /users/assemblers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssemblerResponse {
    private String id;
    private String fullName;
    private String email;
    private String phone;
    private boolean active;
}
