package com.woodfurni.auth.controller;

import com.woodfurni.auth.dto.OtpSendRequest;
import com.woodfurni.auth.dto.OtpSendResponse;
import com.woodfurni.auth.dto.OtpVerifyRequest;
import com.woodfurni.auth.dto.OtpVerifyResponse;
import com.woodfurni.auth.service.EmailOtpService;
import com.woodfurni.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for requesting and verifying email OTPs.
 * Base path: /api/v1/auth/otp
 *
 * Both endpoints are public (no auth required).
 */
@RestController
@RequestMapping("/auth/otp")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Email OTP verification")
public class OtpController {

    private final EmailOtpService emailOtpService;

    @PostMapping("/send")
    @Operation(summary = "Send registration OTP",
            description = "Generates a 6-digit OTP and emails it to the address. "
                    + "Returns 200 + cooldown info if the previous OTP was sent too recently.")
    public ResponseEntity<ApiResponse<OtpSendResponse>> send(
            @Valid @RequestBody OtpSendRequest request) {
        ApiResponse<EmailOtpService.OtpSendResult> result =
                emailOtpService.sendRegistrationOtp(request.getEmail());

        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(result.getMessage()));
        }

        EmailOtpService.OtpSendResult r = result.getData();
        OtpSendResponse body = OtpSendResponse.builder()
                .ttlSeconds(r.ttlSeconds() > 0 ? r.ttlSeconds() : null)
                .cooldownSeconds(r.cooldownSeconds() > 0 ? r.cooldownSeconds() : null)
                .devOtpCode(r.devOtpCode())
                .build();
        return ResponseEntity.ok(ApiResponse.success(result.getMessage(), body));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify registration OTP",
            description = "Validates the 6-digit code and returns a single-use "
                    + "otpToken the client passes to /auth/register.")
    public ResponseEntity<ApiResponse<OtpVerifyResponse>> verify(
            @Valid @RequestBody OtpVerifyRequest request) {
        ApiResponse<EmailOtpService.OtpVerifyResult> result =
                emailOtpService.verifyRegistrationOtp(request.getEmail(), request.getCode());

        if (!result.isSuccess() || result.getData() == null || result.getData().otpToken() == null) {
            int status = "Mã xác nhận không đúng".equals(result.getMessage()) ? 200 : 400;
            // 200 for wrong-code (we want the client to show "wrong code" UI);
            // 400 for hard errors (missing/expired/exceeded).
            if (status == 200 && result.getData() != null) {
                OtpVerifyResponse body = OtpVerifyResponse.builder()
                        .remainingAttempts(result.getData().remainingAttempts())
                        .build();
                return ResponseEntity.ok(ApiResponse.success(result.getMessage(), body));
            }
            return ResponseEntity.badRequest().body(ApiResponse.error(result.getMessage()));
        }

        OtpVerifyResponse body = OtpVerifyResponse.builder()
                .otpToken(result.getData().otpToken())
                .build();
        return ResponseEntity.ok(ApiResponse.success(result.getMessage(), body));
    }
}
