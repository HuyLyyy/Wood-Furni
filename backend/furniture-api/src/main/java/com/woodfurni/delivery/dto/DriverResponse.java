package com.woodfurni.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thông tin tài xế — danh sách trả về từ GET /users/drivers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverResponse {
    private String id;
    private String fullName;
    private String email;
    private String phone;
    private boolean active;
}
