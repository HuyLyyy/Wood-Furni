package com.woodfurni.promotion.dto;

import com.woodfurni.promotion.enums.PromotionType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionRequest {

    @NotBlank(message = "Code is required")
    @Size(min = 3, max = 50, message = "Code must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Code must contain only uppercase letters, numbers, underscores, and hyphens")
    private String code;

    @NotNull(message = "Type is required")
    private PromotionType type;

    @NotNull(message = "Value is required")
    @Positive(message = "Value must be positive")
    private BigDecimal value;

    @PositiveOrZero(message = "Minimum order amount cannot be negative")
    private BigDecimal minOrderAmount;

    @Positive(message = "Max discount amount must be positive")
    private BigDecimal maxDiscountAmount;

    @NotNull(message = "Start date is required")
    private Instant startDate;

    @NotNull(message = "End date is required")
    private Instant endDate;

    @PositiveOrZero(message = "Usage limit cannot be negative")
    private Integer usageLimit;

    private String status;
}
