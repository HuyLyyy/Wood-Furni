package com.woodfurni.auth.controller;

import com.woodfurni.auth.dto.ResetPasswordRequest;
import com.woodfurni.auth.model.User;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.auth.service.AuthService;
import com.woodfurni.auth.service.EmailOtpService;
import com.woodfurni.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Forgot-password reset endpoint.
 *
 * <p>Flow:
 * <ol>
 *   <li>User submits email → POST /auth/otp/forgot-password/send → 6-digit code emailed.</li>
 *   <li>User enters code → POST /auth/otp/forgot-password/verify → single-use otpToken returned.</li>
 *   <li>User submits new password + otpToken → POST /auth/password/reset → password changed.</li>
 * </ol>
 *
 * All endpoints are public. The {@code otpToken} is the only thing
 * proving the requester owns the email — and it's consumed (deleted) on
 * first use.
 */
@Slf4j
@RestController
@RequestMapping("/auth/password")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Password reset flow")
public class PasswordResetController {

    private final EmailOtpService emailOtpService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/reset")
    @Operation(summary = "Reset password using a verified otpToken",
            description = "Consumes the single-use otpToken (issued by "
                    + "/auth/otp/forgot-password/verify) and replaces the "
                    + "user's password. Invalidates the existing refresh "
                    + "token so all sessions are logged out.")
    public ResponseEntity<ApiResponse<Void>> reset(@Valid @RequestBody ResetPasswordRequest request) {
        // 1. Cross-field validation: confirmPassword must match newPassword.
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Mật khẩu nhập lại không khớp"));
        }

        // 2. Consume the otpToken — single-use, also proves email ownership.
        Optional<String> verifiedEmail = emailOtpService.consumeVerifiedToken(
                request.getOtpToken(), EmailOtpService.PURPOSE_FORGOT_PASSWORD);
        if (verifiedEmail.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Mã xác nhận không hợp lệ hoặc đã hết hạn"));
        }

        // 3. Find the user, ensure they exist (race condition guard — token
        //    could outlive the account).
        User user = userRepository.findByEmail(verifiedEmail.get()).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Tài khoản không tồn tại"));
        }

        // 4. Update password hash and revoke refresh tokens so any other
        //    active sessions are forced to re-authenticate.
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setCurrentRefreshToken(null);   // log out everywhere
        userRepository.save(user);

        log.info("Password reset via forgot-password flow for userId={}", user.getId());

        return ResponseEntity.ok(ApiResponse.success(
                "Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại.", null));
    }
}
