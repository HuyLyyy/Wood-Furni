package com.woodfurni.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for {@code POST /auth/password/reset}.
 *
 * The single-use {@code otpToken} is obtained from
 * {@code POST /auth/otp/forgot-password/verify} and proves the requester
 * owns the email address (i.e. they received the OTP and entered the
 * 6-digit code correctly).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "Mã xác nhận là bắt buộc")
    private String otpToken;

    @NotBlank(message = "Mật khẩu mới là bắt buộc")
    @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự")
    private String newPassword;

    @NotBlank(message = "Vui lòng nhập lại mật khẩu mới")
    private String confirmNewPassword;
}
