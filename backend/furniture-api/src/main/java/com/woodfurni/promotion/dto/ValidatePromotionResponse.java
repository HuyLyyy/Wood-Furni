package com.woodfurni.promotion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response for promotion validation.
 * Used in: POST /api/v1/promotions/validate
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidatePromotionResponse {

    private boolean valid;

    private BigDecimal discountAmount;

    private String message;

    private String code;

    private BigDecimal originalAmount;

    public static ValidatePromotionResponse invalid(String message) {
        return ValidatePromotionResponse.builder()
                .valid(false)
                .discountAmount(BigDecimal.ZERO)
                .message(message)
                .build();
    }

    public static ValidatePromotionResponse invalid(String message, String code, BigDecimal originalAmount) {
        return ValidatePromotionResponse.builder()
                .valid(false)
                .discountAmount(BigDecimal.ZERO)
                .message(message)
                .code(code)
                .originalAmount(originalAmount)
                .build();
    }

    public static ValidatePromotionResponse valid(BigDecimal discountAmount, String message, String code, BigDecimal originalAmount) {
        return ValidatePromotionResponse.builder()
                .valid(true)
                .discountAmount(discountAmount)
                .message(message)
                .code(code)
                .originalAmount(originalAmount)
                .build();
    }
}
