package com.woodfurni.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One-time password (OTP) sent to a user's email for verification.
 *
 * Lifecycle:
 *  - created when /auth/otp/send is called
 *  - verified when the user submits the correct code via /auth/otp/verify
 *  - auto-deleted by Mongo TTL index when {@code expiresAt} is reached
 *
 * The raw OTP is never persisted — only its BCrypt hash. Comparison happens
 * via {@link org.springframework.security.crypto.password.PasswordEncoder#matches}.
 *
 * Collection: {@code email_otps}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "email_otps")
@CompoundIndexes({
        @CompoundIndex(name = "email_purpose_idx", def = "{'email': 1, 'purpose': 1}")
})
public class EmailOtp {

    @Id
    private String id;

    /** Normalized email address (lowercased, trimmed) the OTP was sent to. */
    @Indexed
    private String email;

    /** Use case: REGISTER, FORGOT_PASSWORD, ... — keep open for future flows. */
    @Indexed
    private String purpose;

    /** BCrypt hash of the 6-digit code. */
    private String otpHash;

    /** Counter of wrong-code submissions. Reset on success; cleared at TTL. */
    @Builder.Default
    private int attempts = 0;

    /** When this OTP was issued. */
    private Instant createdAt;

    /**
     * Hard expiration. Mongo TTL index will remove the document at this time.
     * Used by the service to decide EXPIRED vs attempts-exceeded.
     */
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    /**
     * Token returned to the client after a successful verify. The actual
     * one-shot token value lives in {@link EmailOtpVerifiedToken} — this
     * field is just a marker so we know this OTP was consumed.
     */
    private Instant verifiedAt;
}
