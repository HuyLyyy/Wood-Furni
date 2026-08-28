package com.woodfurni.promotion.model;

import com.woodfurni.promotion.enums.PromotionStatus;
import com.woodfurni.promotion.enums.PromotionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Promotion / voucher entity.
 * Collection: "promotions"
 *
 * Usage flow:
 * 1. Customer calls POST /promotions/validate → calculates discount (read-only)
 * 2. Order is placed → OrderService calls incrementUsage(code)
 *
 * As defined in WOODFURNI spec Mục 3.3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "promotions")
public class Promotion {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private PromotionType type;

    private BigDecimal value;

    @Builder.Default
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    private BigDecimal maxDiscountAmount;

    private Instant startDate;

    private Instant endDate;

    private Integer usageLimit;

    @Builder.Default
    private Integer usedCount = 0;

    @Builder.Default
    private PromotionStatus status = PromotionStatus.ACTIVE;
}
