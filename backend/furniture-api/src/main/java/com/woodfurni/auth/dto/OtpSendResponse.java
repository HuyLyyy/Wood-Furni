package com.woodfurni.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OtpSendResponse {

    /** Lifetime of the OTP in seconds (e.g. 300 = 5 minutes). */
    private Long ttlSeconds;

    /** Minimum seconds before the user can request another OTP. */
    private Long cooldownSeconds;

    /**
     * Echoed back only when SMTP is bypassed (mail.otp.dev-otp-code is set).
     * Lets the frontend / E2E test grab the code without reading email.
     */
    private String devOtpCode;
}
