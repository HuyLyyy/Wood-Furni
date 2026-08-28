package com.woodfurni.promotion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for customer-facing promotion validation.
 * Called by: POST /api/v1/promotions/validate
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidatePromotionRequest {

    @NotBlank(message = "Code is required")
    private String code;

    @NotNull(message = "Cart total is required")
    @PositiveOrZero(message = "Cart total must be non-negative")
    private BigDecimal cartTotal;
}
