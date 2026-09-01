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
public class OtpVerifyResponse {

    /** Opaque single-use token the client returns at /auth/register. */
    private String otpToken;

    /** When a wrong code is submitted, how many attempts remain. */
    private Integer remainingAttempts;
}
