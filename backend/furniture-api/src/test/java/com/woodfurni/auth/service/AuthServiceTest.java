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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService — registration and login flows.
 *
 * Covers DoD:
 *   - AuthService.register: email trùng → error
 *   - AuthService.login:   password sai → error
 *   - AuthService.login:   happy path → success + tokens
 *
 * Uses pure Mockito (no Spring context).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;

    @InjectMocks private AuthService authService;

    private static final String USER_ID = "user-001";
    private static final String EMAIL = "alice@example.com";
    private static final String RAW_PASSWORD = "Secret#123";
    private static final String HASHED = "bcrypt-hashed-Secret#123";

    @BeforeEach
    void setUp() {
        // JwtProvider exposes getAccessTokenExpirationMs() used by AuthService.
        lenient().when(jwtProvider.getAccessTokenExpirationMs()).thenReturn(3_600_000L);
        lenient().when(jwtProvider.generateAccessToken(anyString(), any(Role.class)))
                .thenReturn("access-token-xyz");
        lenient().when(jwtProvider.generateRefreshToken(anyString(), any(Role.class)))
                .thenReturn("refresh-token-xyz");
    }

    // ============================================================
    // TC-AUTH-01 — register trùng email
    // ============================================================
    @Test
    @DisplayName("register trùng email - trả về ApiResponse.error, không tạo user mới")
    void register_EmailAlreadyExists_ReturnsError() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        RegisterRequest req = RegisterRequest.builder()
                .email(EMAIL)
                .password(RAW_PASSWORD)
                .fullName("Alice")
                .build();

        ApiResponse<AuthResponse> result = authService.register(req);

        assertFalse(result.isSuccess());
        assertEquals("Email đã được sử dụng", result.getMessage());
        assertNull(result.getData());

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    // ============================================================
    // TC-AUTH-02 — register thành công
    // ============================================================
    @Test
    @DisplayName("register email mới - hash password, lưu user, sinh token")
    void register_NewEmail_Succeeds() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", USER_ID);
            return u;
        });

        RegisterRequest req = RegisterRequest.builder()
                .email(EMAIL)
                .password(RAW_PASSWORD)
                .fullName("Alice")
                .phone("0909000123")
                .build();

        ApiResponse<AuthResponse> result = authService.register(req);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("access-token-xyz", result.getData().getAccessToken());
        assertEquals("refresh-token-xyz", result.getData().getRefreshToken());
        assertEquals(USER_ID, result.getData().getUser().getId());

        // Password was hashed with BCrypt, not stored raw
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(savedUser.capture()); // 1x initial + 1x after refresh token
        User persisted = savedUser.getValue();
        assertEquals(HASHED, persisted.getPasswordHash());
        assertNotEquals(RAW_PASSWORD, persisted.getPasswordHash());
        assertEquals(Role.CUSTOMER, persisted.getRole());
        assertEquals(UserStatus.ACTIVE, persisted.getStatus());
    }

    // ============================================================
    // TC-AUTH-03 — login sai password
    // ============================================================
    @Test
    @DisplayName("login sai password - trả về error, không sinh token")
    void login_WrongPassword_ReturnsError() {
        User existing = User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .passwordHash(HASHED)
                .fullName("Alice")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED)).thenReturn(false);

        LoginRequest req = LoginRequest.builder()
                .email(EMAIL)
                .password(RAW_PASSWORD)
                .build();

        ApiResponse<AuthResponse> result = authService.login(req);

        assertFalse(result.isSuccess());
        assertEquals("Email hoặc mật khẩu không đúng", result.getMessage());
        assertNull(result.getData());

        verify(jwtProvider, never()).generateAccessToken(anyString(), any(Role.class));
        verify(jwtProvider, never()).generateRefreshToken(anyString(), any(Role.class));
    }

    // ============================================================
    // TC-AUTH-04 — login email không tồn tại
    // ============================================================
    @Test
    @DisplayName("login email không tồn tại - trả về error generic (không lộ thông tin)")
    void login_EmailNotFound_ReturnsError() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        LoginRequest req = LoginRequest.builder()
                .email(EMAIL)
                .password(RAW_PASSWORD)
                .build();

        ApiResponse<AuthResponse> result = authService.login(req);

        assertFalse(result.isSuccess());
        assertEquals("Email hoặc mật khẩu không đúng", result.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    // ============================================================
    // TC-AUTH-05 — login tài khoản bị vô hiệu hoá
    // ============================================================
    @Test
    @DisplayName("login tài khoản DISABLED - từ chối với message rõ ràng")
    void login_DisabledAccount_ReturnsDisabledError() {
        User disabled = User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .passwordHash(HASHED)
                .role(Role.CUSTOMER)
                .status(UserStatus.DISABLED)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(disabled));

        LoginRequest req = LoginRequest.builder()
                .email(EMAIL)
                .password(RAW_PASSWORD)
                .build();

        ApiResponse<AuthResponse> result = authService.login(req);

        assertFalse(result.isSuccess());
        assertEquals("Tài khoản đã bị vô hiệu hóa", result.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    // ============================================================
    // TC-AUTH-06 — login happy path
    // ============================================================
    @Test
    @DisplayName("login thành công - trả về AuthResponse với access + refresh token")
    void login_ValidCredentials_ReturnsTokens() {
        User existing = User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .passwordHash(HASHED)
                .fullName("Alice")
                .phone("0909000123")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginRequest req = LoginRequest.builder()
                .email(EMAIL)
                .password(RAW_PASSWORD)
                .build();

        ApiResponse<AuthResponse> result = authService.login(req);

        assertTrue(result.isSuccess());
        AuthResponse body = result.getData();
        assertNotNull(body);
        assertEquals("access-token-xyz", body.getAccessToken());
        assertEquals("refresh-token-xyz", body.getRefreshToken());
        assertEquals("Bearer", body.getTokenType());
        assertEquals(3600L, body.getExpiresIn());

        UserSummary summary = body.getUser();
        assertEquals(USER_ID, summary.getId());
        assertEquals(EMAIL, summary.getEmail());
        assertEquals(Role.CUSTOMER, summary.getRole());
        assertEquals(UserStatus.ACTIVE, summary.getStatus());
    }
}
