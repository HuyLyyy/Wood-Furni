package com.woodfurni.auth.service;

import com.woodfurni.auth.dto.AuthResponse;
import com.woodfurni.auth.dto.LoginRequest;
import com.woodfurni.auth.dto.RegisterRequest;
import com.woodfurni.auth.dto.UserSummary;
import com.woodfurni.auth.enums.Role;
import com.woodfurni.auth.enums.UserStatus;
import com.woodfurni.auth.model.User;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.common.ApiResponse;
import com.woodfurni.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Authentication service for WOODFURNI API.
 * Handles user registration, login, token refresh, and logout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final long MILLIS_PER_SECOND = 1000L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /**
     * Register a new user.
     *
     * @param request registration data
     * @return ApiResponse with AuthResponse on success
     */
    public ApiResponse<AuthResponse> register(RegisterRequest request) {
        // Check for duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            return ApiResponse.error("Email đã được sử dụng");
        }

        // Create new user
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        log.info("New user registered: {} with role: {}", savedUser.getEmail(), savedUser.getRole());

        // Generate tokens
        AuthResponse authResponse = generateAuthResponse(savedUser);
        return ApiResponse.success("Đăng ký thành công", authResponse);
    }

    /**
     * Authenticate user and return tokens.
     *
     * @param request login credentials
     * @return ApiResponse with AuthResponse on success
     */
    public ApiResponse<AuthResponse> login(LoginRequest request) {
        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (user == null) {
            return ApiResponse.error("Email hoặc mật khẩu không đúng");
        }

        // Check if user is disabled
        if (user.getStatus() == UserStatus.DISABLED) {
            return ApiResponse.error("Tài khoản đã bị vô hiệu hóa");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ApiResponse.error("Email hoặc mật khẩu không đúng");
        }

        log.info("User logged in: {}", user.getEmail());

        // Generate tokens
        AuthResponse authResponse = generateAuthResponse(user);
        return ApiResponse.success("Đăng nhập thành công", authResponse);
    }

    /**
     * Refresh access token using a valid refresh token.
     *
     * @param refreshToken the refresh token
     * @return ApiResponse with new AuthResponse
     */
    public ApiResponse<AuthResponse> refresh(String refreshToken) {
        // Validate refresh token
        if (!jwtProvider.validateToken(refreshToken)) {
            return ApiResponse.error("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        // Extract user ID from token
        String userId = jwtProvider.getUserIdFromToken(refreshToken);

        // Find user
        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            return ApiResponse.error("Người dùng không tồn tại");
        }

        // Check if user's refresh token matches (revocation check)
        if (user.getCurrentRefreshToken() != null
                && !user.getCurrentRefreshToken().equals(refreshToken)) {
            return ApiResponse.error("Refresh token đã bị thu hồi");
        }

        // Check if user is disabled
        if (user.getStatus() == UserStatus.DISABLED) {
            return ApiResponse.error("Tài khoản đã bị vô hiệu hóa");
        }

        log.info("Token refreshed for user: {}", user.getEmail());

        // Generate new tokens
        AuthResponse authResponse = generateAuthResponse(user);
        return ApiResponse.success("Làm mới token thành công", authResponse);
    }

    /**
     * Logout user by revoking refresh token.
     * For stateless JWT, we revoke the refresh token by clearing it from DB.
     *
     * @param userId the user ID from SecurityContext
     * @return ApiResponse with success message
     */
    public ApiResponse<Void> logout(String userId) {
        User user = userRepository.findById(userId)
                .orElse(null);

        if (user != null) {
            user.setCurrentRefreshToken(null);
            userRepository.save(user);
            log.info("User logged out: {}", user.getEmail());
        }

        return ApiResponse.success("Đăng xuất thành công");
    }

    /**
     * Get user summary by ID.
     *
     * @param userId the user ID
     * @return UserSummary or null if not found
     */
    public UserSummary getUserSummary(String userId) {
        return userRepository.findById(userId)
                .map(this::toUserSummary)
                .orElse(null);
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), user.getRole());

        // Save refresh token to user for revocation
        user.setCurrentRefreshToken(refreshToken);
        userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(TOKEN_TYPE)
                .expiresIn(jwtProvider.getAccessTokenExpirationMs() / MILLIS_PER_SECOND)
                .user(toUserSummary(user))
                .build();
    }

    private UserSummary toUserSummary(User user) {
        return UserSummary.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }
}
