package com.woodfurni.auth.controller;

import com.woodfurni.auth.dto.AuthResponse;
import com.woodfurni.auth.dto.LoginRequest;
import com.woodfurni.auth.dto.RefreshTokenRequest;
import com.woodfurni.auth.dto.RegisterRequest;
import com.woodfurni.auth.dto.UserSummary;
import com.woodfurni.auth.service.AuthService;
import com.woodfurni.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 * Base path: /api/v1/auth
 *
 * Note: context-path /api/v1 is configured in application.yml, so this
 * controller's path /auth resolves to /api/v1/auth at runtime.
 *
 * All responses are wrapped in ApiResponse format.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and authorization")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account with CUSTOMER role")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and return JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        ApiResponse<AuthResponse> response = authService.login(request);
        if (!response.isSuccess()) {
            return ResponseEntity.status(401).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get new access token using valid refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get authenticated user info from SecurityContext", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserSummary>> getCurrentUser(Authentication authentication) {
        String userId = authentication.getName();
        UserSummary user = authService.getUserSummary(userId);

        if (user == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("Người dùng không tồn tại"));
        }

        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoke refresh token (stateless - FE must delete tokens)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(authService.logout(userId));
    }
}
