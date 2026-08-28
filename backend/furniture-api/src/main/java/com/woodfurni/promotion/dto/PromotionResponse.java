package com.woodfurni.promotion.dto;

import com.woodfurni.promotion.enums.PromotionStatus;
import com.woodfurni.promotion.enums.PromotionType;
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
public class PromotionResponse {

    private String id;
    private String code;
    private PromotionType type;
    private BigDecimal value;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;
    private Instant startDate;
    private Instant endDate;
    private Integer usageLimit;
    private Integer usedCount;
    private PromotionStatus status;
}
