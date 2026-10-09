package com.woodfurni.promotion.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.woodfurni.common.FlexibleInstantDeserializer;
import com.woodfurni.common.VietnamInstantSerializer;
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
     * Parsed from multiple input formats:
     * <ul>
     *   <li>"2026-10-01T08:49:00.000Z" — browser JSON (ISO UTC)</li>
     *   <li>"2026-10-01T08:49:00Z" — ISO without millis</li>
     *   <li>"2026-10-01T08:49:00+07:00" — ISO with VN offset</li>
     *   <li>"2026-10-01T08:49" — HTML datetime-local (VN wall-clock)</li>
     * </ul>
     * Stored in DB as {@code Instant} (UTC).
     */
    @NotNull(message = "Start date is required")
    @JsonDeserialize(using = FlexibleInstantDeserializer.class)
    @JsonSerialize(using = VietnamInstantSerializer.class)
    private Instant startDate;

    /**
     * Same flexible parsing as {@link #startDate}.
     * Stored in DB as {@code Instant} (UTC).
     */
    @NotNull(message = "End date is required")
    @JsonDeserialize(using = FlexibleInstantDeserializer.class)
    @JsonSerialize(using = VietnamInstantSerializer.class)
    private Instant endDate;

    @PositiveOrZero(message = "Usage limit cannot be negative")
    private Integer usageLimit;

    private String status;
}
