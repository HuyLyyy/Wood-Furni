package com.woodfurni.promotion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    /**
     * Parsed from HTML &lt;input type="datetime-local"&gt; value ("yyyy-MM-ddTHH:mm")
     * sent by the admin frontend.
     * Jackson deserialises this using {@link #DATE_TIME_LOCAL_FORMAT}.
     * Stored in DB as {@code Instant} (UTC).
     */
    @NotNull(message = "Start date is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm", timezone = "Asia/Ho_Chi_Minh")
    private Instant startDate;

    /**
     * Parsed from HTML &lt;input type="datetime-local"&gt; value ("yyyy-MM-ddTHH:mm")
     * sent by the admin frontend.
     * Jackson deserialises this using {@link #DATE_TIME_LOCAL_FORMAT}.
     * Stored in DB as {@code Instant} (UTC).
     */
    @NotNull(message = "End date is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm", timezone = "Asia/Ho_Chi_Minh")
    private Instant endDate;

    @PositiveOrZero(message = "Usage limit cannot be negative")
    private Integer usageLimit;

    private String status;
}
