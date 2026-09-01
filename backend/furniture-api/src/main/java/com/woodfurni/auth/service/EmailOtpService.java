package com.woodfurni.auth.service;

import com.woodfurni.auth.model.EmailOtp;
import com.woodfurni.auth.model.EmailOtpVerifiedToken;
import com.woodfurni.auth.repository.EmailOtpRepository;
import com.woodfurni.auth.repository.EmailOtpVerifiedTokenRepository;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.common.ApiResponse;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
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

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailOtpRepository emailOtpRepository;
    private final EmailOtpVerifiedTokenRepository verifiedTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${mail.from}")
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

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

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
        String email = normalizeEmail(rawEmail);

        if (userRepository.existsByEmail(email)) {
            return ApiResponse.error("Email đã được sử dụng");
        }

        Optional<EmailOtp> existing = emailOtpRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(email, PURPOSE_REGISTER);

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
                .purpose(PURPOSE_REGISTER)
                .otpHash(passwordEncoder.encode(code))
                .attempts(0)
                .createdAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .verifiedAt(null)
                .build();
        emailOtpRepository.save(otp);

        // Decide whether to send via SMTP or fall back to dev mode.
        // Dev mode is used when EITHER:
        //   - DEV_OTP_CODE is explicitly set (forces a fixed code), OR
        //   - SMTP credentials are missing (no MAIL_USERNAME/MAIL_PASSWORD
        //     configured, e.g. stale Render env) — so the deploy doesn't
        //     completely block registration.
        String configDevCode = (devOtpCode == null || devOtpCode.isBlank()) ? null : devOtpCode;
        boolean smtpConfigured = smtpUsername != null && !smtpUsername.isBlank()
                && smtpPassword != null && !smtpPassword.isBlank();
        boolean forceDev = configDevCode != null || !smtpConfigured;

        String outboundCode = forceDev ? (configDevCode != null ? configDevCode : code) : null;
        boolean sentViaMail = false;

        if (!forceDev) {
            try {
                sendOtpEmail(email, code, ttlSeconds);
                sentViaMail = true;
            } catch (Exception ex) {
                log.error("Failed to send OTP email to {}: {}", email, ex.toString());
                // Fall back: surface the code in the response so the user can
                // still register. The OTP remains valid (it's hashed in DB).
                outboundCode = code;
            }
        } else {
            log.warn("DEV OTP for {} = {} (smtpConfigured={}, devOtpCode set={})",
                    email, outboundCode, smtpConfigured, configDevCode != null);
        }

        OtpSendResult data = OtpSendResult.success(
                ttlSeconds,
                cooldownSeconds,
                outboundCode,
                sentViaMail ? null : outboundCode);
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
        String email = normalizeEmail(rawEmail);

        if (code == null || code.isBlank()) {
            return ApiResponse.error("Vui lòng nhập mã xác nhận");
        }

        Optional<EmailOtp> maybeOtp = emailOtpRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(email, PURPOSE_REGISTER);

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
        verifiedTokenRepository.deleteByEmailAndPurpose(email, PURPOSE_REGISTER);

        String tokenValue = UUID.randomUUID().toString();
        EmailOtpVerifiedToken token = EmailOtpVerifiedToken.builder()
                .token(tokenValue)
                .email(email)
                .purpose(PURPOSE_REGISTER)
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

    private void sendOtpEmail(String to, String code, long ttl) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                message, true, StandardCharsets.UTF_8.name());
        try {
            helper.setFrom(mailFrom, mailFromName);
        } catch (java.io.UnsupportedEncodingException ex) {
            // UTF-8 is guaranteed by the JVM; this should never happen.
            throw new IllegalStateException("UTF-8 charset unavailable", ex);
        }
        helper.setTo(to);
        helper.setSubject("Mã xác nhận đăng ký WOODFURNI");
        helper.setText(buildHtmlBody(code, ttl), true);
        mailSender.send(message);
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

    // ------------------------------------------------------------------------
    // Result DTOs (internal — kept here so they don't pollute the public DTO
    // package; controllers adapt them into OtpSendResponse / OtpVerifyResponse).
    // ------------------------------------------------------------------------

    public record OtpSendResult(long ttlSeconds, long cooldownSeconds, String devOtpCode) {
        public static OtpSendResult success(long ttl, long cooldown, String dev, String ignored) {
            return new OtpSendResult(ttl, cooldown, dev);
        }
        public static OtpSendResult cooldown(long waitSeconds) {
            return new OtpSendResult(0, waitSeconds, null);
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
