package com.woodfurni.promotion.service;

import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.promotion.dto.PromotionRequest;
import com.woodfurni.promotion.dto.PromotionResponse;
import com.woodfurni.promotion.dto.ValidatePromotionRequest;
import com.woodfurni.promotion.dto.ValidatePromotionResponse;
import com.woodfurni.promotion.enums.PromotionStatus;
import com.woodfurni.promotion.enums.PromotionType;
import com.woodfurni.promotion.model.Promotion;
import com.woodfurni.promotion.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Promotion / voucher management service.
 *
 * Usage patterns:
 * - Customer checkout: validateAndCalculate() → returns discount amount (read-only)
 * - Order creation: incrementUsage() called by OrderService
 */
@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Validate a promotion code and calculate the discount amount.
     * This is a READ-ONLY operation — does NOT increment usage count.
     *
     * Validation checks (in order):
     * 1. Code exists
     * 2. Status = ACTIVE
     * 3. Current time is within startDate - endDate
     * 4. orderAmount >= minOrderAmount
     * 5. usedCount < usageLimit (if usageLimit is set)
     *
     * Discount calculation:
     * - PERCENTAGE: value% of orderAmount, capped at maxDiscountAmount (if set)
     * - FIXED_AMOUNT: value (capped at orderAmount, never exceeds it)
     */
    public ValidatePromotionResponse validateAndCalculate(ValidatePromotionRequest request) {
        return validateAndCalculate(request.getCode(), request.getCartTotal());
    }

    public ValidatePromotionResponse validateAndCalculate(String code, BigDecimal orderAmount) {
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code.trim().toUpperCase())
                .orElse(null);

        if (promotion == null) {
            return ValidatePromotionResponse.invalid("Mã khuyến mãi không tồn tại", code, orderAmount);
        }

        Instant now = Instant.now();

        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            return ValidatePromotionResponse.invalid(
                    "Mã khuyến mãi đã bị vô hiệu hóa", code, orderAmount);
        }

        if (now.isBefore(promotion.getStartDate())) {
            return ValidatePromotionResponse.invalid(
                    "Mã khuyến mãi chưa có hiệu lực", code, orderAmount);
        }

        if (now.isAfter(promotion.getEndDate())) {
            return ValidatePromotionResponse.invalid(
                    "Mã khuyến mãi đã hết hạn", code, orderAmount);
        }

        BigDecimal minOrder = promotion.getMinOrderAmount();
        if (minOrder == null) {
            minOrder = BigDecimal.ZERO;
        }
        if (orderAmount.compareTo(minOrder) < 0) {
            return ValidatePromotionResponse.invalid(
                    String.format("Đơn hàng tối thiểu %s để áp dụng mã này", minOrder), code, orderAmount);
        }

        if (promotion.getUsageLimit() != null && promotion.getUsedCount() >= promotion.getUsageLimit()) {
            return ValidatePromotionResponse.invalid(
                    "Mã khuyến mãi đã hết lượt sử dụng", code, orderAmount);
        }

        BigDecimal discount = calculateDiscount(promotion, orderAmount);

        return ValidatePromotionResponse.valid(
                discount,
                "Áp dụng mã khuyến mãi thành công",
                code,
                orderAmount);
    }

    /**
     * Calculate discount amount based on promotion type.
     * - PERCENTAGE: (value / 100) * orderAmount, capped at maxDiscountAmount
     * - FIXED_AMOUNT: min(value, orderAmount) — never exceeds order
     */
    public BigDecimal calculateDiscount(Promotion promotion, BigDecimal orderAmount) {
        BigDecimal discount;

        if (promotion.getType() == PromotionType.PERCENTAGE) {
            discount = orderAmount
                    .multiply(promotion.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            if (promotion.getMaxDiscountAmount() != null
                    && discount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
                discount = promotion.getMaxDiscountAmount();
            }
        } else {
            discount = promotion.getValue();
            if (discount.compareTo(orderAmount) > 0) {
                discount = orderAmount;
            }
        }

        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Increment usage count for a promotion.
     * Called by OrderService when an order is successfully placed.
     * Uses atomic findAndModify to prevent race conditions.
     */
    public void incrementUsage(String code) {
        Query query = new Query(Criteria.where("code").is(code.trim().toUpperCase()));
        Update update = new Update().inc("usedCount", 1);

        Promotion result = mongoTemplate.findAndModify(query, update, Promotion.class);

        if (result == null) {
            throw new EntityNotFoundException("Promotion not found: " + code);
        }
    }

    public PromotionResponse create(PromotionRequest request) {
        String code = request.getCode().trim().toUpperCase();

        if (promotionRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Promotion code already exists: " + code);
        }

        if (request.getEndDate() != null && request.getStartDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        if (request.getType() == PromotionType.PERCENTAGE
                && request.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage value cannot exceed 100");
        }

        Promotion promotion = Promotion.builder()
                .code(code)
                .type(request.getType())
                .value(request.getValue())
                .minOrderAmount(request.getMinOrderAmount() != null ? request.getMinOrderAmount() : BigDecimal.ZERO)
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .usageLimit(request.getUsageLimit())
                .usedCount(0)
                .status(PromotionStatus.ACTIVE)
                .build();

        Promotion saved = promotionRepository.save(promotion);
        return toResponse(saved);
    }

    public PromotionResponse update(String id, PromotionRequest request) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Promotion not found: " + id));

        if (request.getCode() != null && !request.getCode().equalsIgnoreCase(promotion.getCode())) {
            String newCode = request.getCode().trim().toUpperCase();
            if (promotionRepository.existsByCode(newCode)) {
                throw new IllegalArgumentException("Promotion code already exists: " + newCode);
            }
            promotion.setCode(newCode);
        }

        if (request.getType() != null) {
            promotion.setType(request.getType());
        }
        if (request.getValue() != null) {
            promotion.setValue(request.getValue());
        }
        if (request.getMinOrderAmount() != null) {
            promotion.setMinOrderAmount(request.getMinOrderAmount());
        }
        if (request.getMaxDiscountAmount() != null) {
            promotion.setMaxDiscountAmount(request.getMaxDiscountAmount());
        }
        if (request.getStartDate() != null) {
            promotion.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            promotion.setEndDate(request.getEndDate());
        }
        if (request.getUsageLimit() != null) {
            promotion.setUsageLimit(request.getUsageLimit());
        }

        Promotion saved = promotionRepository.save(promotion);
        return toResponse(saved);
    }

    public PromotionResponse getById(String id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Promotion not found: " + id));
        return toResponse(promotion);
    }

    public List<PromotionResponse> getAll() {
        return promotionRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PromotionResponse changeStatus(String id, PromotionStatus newStatus) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Promotion not found: " + id));

        promotion.setStatus(newStatus);
        Promotion saved = promotionRepository.save(promotion);
        return toResponse(saved);
    }

    public void delete(String id) {
        if (!promotionRepository.existsById(id)) {
            throw new EntityNotFoundException("Promotion not found: " + id);
        }
        promotionRepository.deleteById(id);
    }

    private PromotionResponse toResponse(Promotion promotion) {
        return PromotionResponse.builder()
                .id(promotion.getId())
                .code(promotion.getCode())
                .type(promotion.getType())
                .value(promotion.getValue())
                .minOrderAmount(promotion.getMinOrderAmount())
                .maxDiscountAmount(promotion.getMaxDiscountAmount())
                .startDate(promotion.getStartDate())
                .endDate(promotion.getEndDate())
                .usageLimit(promotion.getUsageLimit())
                .usedCount(promotion.getUsedCount())
                .status(promotion.getStatus())
                .build();
    }
}
