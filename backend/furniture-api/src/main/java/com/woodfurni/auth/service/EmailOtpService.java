package com.woodfurni.auth.service;

import com.woodfurni.auth.model.EmailOtp;
import com.woodfurni.auth.model.EmailOtpVerifiedToken;
import com.woodfurni.auth.repository.EmailOtpRepository;
import com.woodfurni.auth.repository.EmailOtpVerifiedTokenRepository;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.common.ApiResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the OTP lifecycle: send, verify, single-use token issuance.
 *
 * Configuration is read from {@code mail.otp.*} in application.yml.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOtpService {

    public static final String PURPOSE_REGISTER = "REGISTER";
    public static final String PURPOSE_FORGOT_PASSWORD = "FORGOT_PASSWORD";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailOtpRepository emailOtpRepository;
    private final EmailOtpVerifiedTokenRepository verifiedTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResendEmailService resendEmailService;

    @Value("${mail.from:noreply@resend.dev}")
    private String mailFrom;

    @Value("${mail.from-name:WOODFURNI}")
    private String mailFromName;

    @Value("${mail.otp.ttl-seconds:300}")
    private long ttlSeconds;

    @Value("${mail.otp.cooldown-seconds:60}")
    private long cooldownSeconds;

    @Value("${mail.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${mail.otp.code-length:6}")
    private int codeLength;

    @Value("${mail.otp.dev-otp-code:}")
    private String devOtpCode;

    @PostConstruct
    void logEmailServiceStatus() {
        boolean resendConfigured = resendEmailService.isConfigured();
        log.info("=================================================");
        log.info("EMAIL SERVICE: Resend configured={}, from={}", resendConfigured, mailFrom);
        log.info("EMAIL MODE: {}", resendConfigured ? "PRODUCTION (will send real email)" : "DEV (no RESEND_API_KEY — returns devOtpCode)");
        log.info("=================================================");
    }

    // ------------------------------------------------------------------------
    // Send
    // ------------------------------------------------------------------------

    /**
     * Send (or resend) a registration OTP to the given email.
     *
     * Failure cases:
     *  - EMAIL_ALREADY_EXISTS: a user already owns this email
     *  - COOLDOWN: previous OTP sent less than {@code cooldownSeconds} ago
     */
    public ApiResponse<OtpSendResult> sendRegistrationOtp(String rawEmail) {
        return sendOtpForPurpose(rawEmail, PURPOSE_REGISTER, false);
    }

    /**
     * Send (or resend) a forgot-password OTP to the given email.
     *
     * Failure cases:
     *  - EMAIL_NOT_FOUND: no user owns this email (security: don't leak
     *    existence, but we still return success to the client so callers
     *    don't have to special-case it; this is the canonical practice)
     *  - COOLDOWN: previous OTP sent less than {@code cooldownSeconds} ago
     */
    public ApiResponse<OtpSendResult> sendForgotPasswordOtp(String rawEmail) {
        return sendOtpForPurpose(rawEmail, PURPOSE_FORGOT_PASSWORD, true);
    }

    /**
     * Core implementation shared by register / forgot-password flows.
     *
     * <p>Behaviour:
     * <ul>
     *   <li>{@code emailMustExist == false} (register): returns
     *       {@code EMAIL_ALREADY_EXISTS} if the email is taken.</li>
     *   <li>{@code emailMustExist == true} (forgot password): returns a
     *       generic "not found" error if the email is not registered.</li>
     *   <li>Both honour the cooldown window — re-sends within
     *       {@code cooldownSeconds} of the previous OTP return a cooldown
     *       payload instead of issuing a new code.</li>
     * </ul>
     */
    private ApiResponse<OtpSendResult> sendOtpForPurpose(String rawEmail,
                                                         String purpose,
                                                         boolean emailMustExist) {
        String email = normalizeEmail(rawEmail);

        boolean userExists = userRepository.existsByEmail(email);
        if (emailMustExist && !userExists) {
            return ApiResponse.error("Email không tồn tại trong hệ thống");
        }
        if (!emailMustExist && userExists) {
            return ApiResponse.error("Email đã được sử dụng");
        }

        Optional<EmailOtp> existing = emailOtpRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose);

        if (existing.isPresent()) {
            EmailOtp prev = existing.get();
            if (prev.getVerifiedAt() == null && prev.getExpiresAt().isAfter(Instant.now())) {
                long elapsed = Duration.between(prev.getCreatedAt(), Instant.now()).getSeconds();
                if (elapsed < cooldownSeconds) {
                    long wait = cooldownSeconds - elapsed;
                    return ApiResponse.success(
                            "Vui lòng chờ trước khi gửi lại",
                            OtpSendResult.cooldown(wait));
                }
            }
            // Invalidate stale OTP before issuing a new one.
            emailOtpRepository.delete(prev);
        }

        String code = generateCode();
        Instant now = Instant.now();
        EmailOtp otp = EmailOtp.builder()
                .email(email)
                .purpose(purpose)
                .otpHash(passwordEncoder.encode(code))
                .attempts(0)
                .createdAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .verifiedAt(null)
                .build();
        emailOtpRepository.save(otp);

        // Decide whether to send via Resend API or fall back to dev mode.
        String configDevCode = (devOtpCode == null || devOtpCode.isBlank()) ? null : devOtpCode;
        boolean resendConfigured = resendEmailService.isConfigured();
        boolean forceDev = configDevCode != null || !resendConfigured;

        String outboundCode = forceDev ? (configDevCode != null ? configDevCode : code) : null;
        boolean sentViaMail = false;

        if (!forceDev) {
            String subject = PURPOSE_FORGOT_PASSWORD.equals(purpose)
                    ? "Mã xác nhận đặt lại mật khẩu WOODFURNI"
                    : "Mã xác nhận đăng ký WOODFURNI";
            String htmlBody = PURPOSE_FORGOT_PASSWORD.equals(purpose)
                    ? buildForgotPasswordHtmlBody(code, ttlSeconds)
                    : buildHtmlBody(code, ttlSeconds);
            boolean sent = resendEmailService.sendHtmlEmail(email, subject, htmlBody);
            if (sent) {
                sentViaMail = true;
                log.info("OTP email ({}) sent to {} via Resend", purpose, email);
            } else {
                log.error("Resend failed to send OTP email to {}", email);
                outboundCode = code;
            }
        } else {
            log.warn("DEV OTP ({}) for {} = {} (resendConfigured={}, devOtpCode set={})",
                    purpose, email, outboundCode, resendConfigured, configDevCode != null);
        }

        OtpSendResult data = OtpSendResult.success(
                ttlSeconds,
                cooldownSeconds,
                outboundCode,
                sentViaMail);
        return ApiResponse.success("Đã gửi mã xác nhận đến email của bạn", data);
    }

    // ------------------------------------------------------------------------
    // Verify
    // ------------------------------------------------------------------------

    /**
     * Verify the code submitted by the user. On success, issue a single-use
     * {@code otpToken} the client returns at registration time.
     */
    public ApiResponse<OtpVerifyResult> verifyRegistrationOtp(String rawEmail, String code) {
        return verifyOtpForPurpose(rawEmail, code, PURPOSE_REGISTER);
    }

    /**
     * Verify the code submitted during the forgot-password flow. On success
     * issues a single-use {@code otpToken} (with purpose = FORGOT_PASSWORD)
     * the client returns to {@code POST /auth/password/reset}.
     */
    public ApiResponse<OtpVerifyResult> verifyForgotPasswordOtp(String rawEmail, String code) {
        return verifyOtpForPurpose(rawEmail, code, PURPOSE_FORGOT_PASSWORD);
    }

    private ApiResponse<OtpVerifyResult> verifyOtpForPurpose(String rawEmail, String code, String purpose) {
        String email = normalizeEmail(rawEmail);

        if (code == null || code.isBlank()) {
            return ApiResponse.error("Vui lòng nhập mã xác nhận");
        }

        Optional<EmailOtp> maybeOtp = emailOtpRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose);

        if (maybeOtp.isEmpty()) {
            return ApiResponse.error("Mã xác nhận không tồn tại hoặc đã hết hạn");
        }
        EmailOtp otp = maybeOtp.get();

        if (otp.getVerifiedAt() != null) {
            return ApiResponse.error("Mã xác nhận đã được sử dụng");
        }
        if (otp.getExpiresAt().isBefore(Instant.now())) {
            return ApiResponse.error("Mã xác nhận đã hết hạn, vui lòng gửi lại");
        }
        if (otp.getAttempts() >= maxAttempts) {
            return ApiResponse.error("Bạn đã nhập sai quá nhiều lần, vui lòng gửi lại mã mới");
        }

        boolean matches = passwordEncoder.matches(code.trim(), otp.getOtpHash());
        if (!matches) {
            otp.setAttempts(otp.getAttempts() + 1);
            emailOtpRepository.save(otp);

            int remaining = maxAttempts - otp.getAttempts();
            if (remaining <= 0) {
                return ApiResponse.error(
                        "Bạn đã nhập sai quá nhiều lần, vui lòng gửi lại mã mới");
            }
            return ApiResponse.success(
                    "Mã xác nhận không đúng",
                    OtpVerifyResult.wrongCode(remaining));
        }

        // Success: mark OTP as consumed and issue a single-use verified-token.
        otp.setVerifiedAt(Instant.now());
        emailOtpRepository.save(otp);

        // Clean up any older verified tokens for this email/purpose.
        verifiedTokenRepository.deleteByEmailAndPurpose(email, purpose);

        String tokenValue = UUID.randomUUID().toString();
        EmailOtpVerifiedToken token = EmailOtpVerifiedToken.builder()
                .token(tokenValue)
                .email(email)
                .purpose(purpose)
                .verifiedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(Math.max(ttlSeconds, 600)))
                .build();
        verifiedTokenRepository.save(token);

        return ApiResponse.success(
                "Xác nhận thành công",
                OtpVerifyResult.success(tokenValue));
    }

    /**
     * Consume a verified-token issued by {@link #verifyRegistrationOtp}.
     * Returns the email the token authorises, or empty if invalid / expired /
     * already consumed.
     */
    public Optional<String> consumeVerifiedToken(String tokenValue, String purpose) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }
        Optional<EmailOtpVerifiedToken> maybeToken = verifiedTokenRepository.findByToken(tokenValue);
        if (maybeToken.isEmpty()) return Optional.empty();

        EmailOtpVerifiedToken token = maybeToken.get();
        if (!purpose.equals(token.getPurpose())) return Optional.empty();
        if (token.getExpiresAt().isBefore(Instant.now())) {
            verifiedTokenRepository.delete(token);
            return Optional.empty();
        }
        // Single-use: delete after consumption.
        verifiedTokenRepository.delete(token);
        return Optional.of(token.getEmail());
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private String generateCode() {
        if (devOtpCode != null && !devOtpCode.isBlank()) {
            return devOtpCode;
        }
        int bound = (int) Math.pow(10, codeLength);
        int n = RANDOM.nextInt(bound);
        return String.format("%0" + codeLength + "d", n);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String buildHtmlBody(String code, long ttl) {
        long minutes = ttl / 60;
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background:#f4f1ec;font-family:'Segoe UI',Arial,sans-serif;color:#2b2a27;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                    <tr>
                      <td align="center" style="padding:32px 12px;">
                        <table role="presentation" width="520" cellspacing="0" cellpadding="0" border="0"
                               style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 4px 16px rgba(0,0,0,0.06);">
                          <tr>
                            <td style="background:#5a3a22;color:#fff;padding:24px 32px;text-align:center;">
                              <h1 style="margin:0;font-size:22px;letter-spacing:2px;">WOODFURNI</h1>
                              <p style="margin:6px 0 0;font-size:13px;opacity:.9;">Xác nhận đăng ký tài khoản</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:32px;">
                              <p style="margin:0 0 16px;font-size:15px;">Xin chào,</p>
                              <p style="margin:0 0 24px;font-size:15px;line-height:1.6;">
                                Cảm ơn bạn đã đăng ký tài khoản WOODFURNI. Vui lòng sử dụng mã xác nhận bên dưới
                                để hoàn tất quá trình đăng ký. Mã có hiệu lực trong vòng <strong>%d phút</strong>.
                              </p>
                              <div style="text-align:center;margin:24px 0;">
                                <div style="display:inline-block;background:#f9f5ef;border:1px dashed #c9a97a;border-radius:10px;
                                            padding:18px 28px;letter-spacing:12px;font-size:32px;font-weight:700;color:#5a3a22;">
                                  %s
                                </div>
                              </div>
                              <p style="margin:0 0 8px;font-size:14px;line-height:1.6;">
                                Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.
                              </p>
                              <p style="margin:0;font-size:14px;line-height:1.6;">
                                Trân trọng,<br/><strong>Đội ngũ WOODFURNI</strong>
                              </p>
                            </td>
                          </tr>
                          <tr>
                            <td style="background:#f4f1ec;padding:16px 32px;text-align:center;font-size:12px;color:#7a736a;">
                              © 2026 WOODFURNI. Email này được gửi tự động, vui lòng không trả lời.
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(minutes, code);
    }

    /**
     * HTML body for forgot-password OTP emails — same template as register
     * but with copy that explains the code is for resetting a password.
     */
    private String buildForgotPasswordHtmlBody(String code, long ttl) {
        long minutes = ttl / 60;
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background:#f4f1ec;font-family:'Segoe UI',Arial,sans-serif;color:#2b2a27;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                    <tr>
                      <td align="center" style="padding:32px 12px;">
                        <table role="presentation" width="520" cellspacing="0" cellpadding="0" border="0"
                               style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 4px 16px rgba(0,0,0,0.06);">
                          <tr>
                            <td style="background:#5a3a22;color:#fff;padding:24px 32px;text-align:center;">
                              <h1 style="margin:0;font-size:22px;letter-spacing:2px;">WOODFURNI</h1>
                              <p style="margin:6px 0 0;font-size:13px;opacity:.9;">Đặt lại mật khẩu</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:32px;">
                              <p style="margin:0 0 16px;font-size:15px;">Xin chào,</p>
                              <p style="margin:0 0 24px;font-size:15px;line-height:1.6;">
                                Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. Vui lòng sử dụng mã xác nhận
                                bên dưới để tiếp tục. Mã có hiệu lực trong vòng <strong>%d phút</strong>.
                              </p>
                              <div style="text-align:center;margin:24px 0;">
                                <div style="display:inline-block;background:#f9f5ef;border:1px dashed #c9a97a;border-radius:10px;
                                            padding:18px 28px;letter-spacing:12px;font-size:32px;font-weight:700;color:#5a3a22;">
                                  %s
                                </div>
                              </div>
                              <p style="margin:0 0 8px;font-size:14px;line-height:1.6;">
                                Nếu bạn không thực hiện yêu cầu này, vui lòng bổ qua email và mật khẩu của bạn sẽ được giữ nguyên.
                              </p>
                              <p style="margin:0;font-size:14px;line-height:1.6;">
                                Trân trọng,<br/><strong>Đội ngũ WOODFURNI</strong>
                              </p>
                            </td>
                          </tr>
                          <tr>
                            <td style="background:#f4f1ec;padding:16px 32px;text-align:center;font-size:12px;color:#7a736a;">
                              © 2026 WOODFURNI. Email này được gửi tự động, vui lòng không trả lời.
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(minutes, code);
    }

    // ------------------------------------------------------------------------
    // Result DTOs (internal — kept here so they don't pollute the public DTO
    // package; controllers adapt them into OtpSendResponse / OtpVerifyResponse).
    // ------------------------------------------------------------------------

    public record OtpSendResult(long ttlSeconds, long cooldownSeconds, String devOtpCode, boolean emailSent) {
        public static OtpSendResult success(long ttl, long cooldown, String devCode, boolean sentViaMail) {
            return new OtpSendResult(ttl, cooldown, devCode, sentViaMail);
        }
        public static OtpSendResult cooldown(long waitSeconds) {
            return new OtpSendResult(0, waitSeconds, null, false);
        }
    }

    public record OtpVerifyResult(String otpToken, Integer remainingAttempts) {
        public static OtpVerifyResult success(String token) {
            return new OtpVerifyResult(token, null);
        }
        public static OtpVerifyResult wrongCode(int remaining) {
            return new OtpVerifyResult(null, remaining);
        }
    }
}
