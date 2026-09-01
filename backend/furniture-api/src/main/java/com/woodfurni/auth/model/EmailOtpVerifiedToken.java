package com.woodfurni.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Short-lived, single-use token issued after a successful OTP verification.
 * The client returns this token to {@code POST /auth/register} (or future
 * flows) to prove the email was verified.
 *
 * Auto-deleted by Mongo TTL index after {@code expiresAt}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "email_otp_verified_tokens")
public class EmailOtpVerifiedToken {

    @Id
    private String id;

    /** The opaque token value the client uses (UUID). Indexed unique. */
    @Indexed(unique = true)
    private String token;

    /** Normalized email this token authorises. */
    @Indexed
    private String email;

    /** Same purpose discriminator used at OTP send time. */
    private String purpose;

    /** When the original OTP was verified. */
    private Instant verifiedAt;

    /**
     * TTL — single-use tokens are short-lived (10 min by default).
     * Document is removed automatically by Mongo when this timestamp passes.
     */
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;
}
