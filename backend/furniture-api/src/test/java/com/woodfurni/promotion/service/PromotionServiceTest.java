package com.woodfurni.promotion.service;

import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.promotion.dto.ValidatePromotionResponse;
import com.woodfurni.promotion.enums.PromotionStatus;
import com.woodfurni.promotion.enums.PromotionType;
import com.woodfurni.promotion.model.Promotion;
import com.woodfurni.promotion.repository.PromotionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PromotionService — voucher validation & discount calculation.
 *
 * Covers DoD:
 *   - TC-PROMO-01: voucher hết hạn → valid: false
 *   - TC-PROMO-02: PERCENTAGE cap ở maxDiscountAmount
 *   - TC-PROMO-03: minOrderAmount không thoả → valid: false
 *   - TC-PROMO-04: code không tồn tại → valid: false
 *   - TC-PROMO-05: usedCount >= usageLimit → valid: false
 *   - TC-PROMO-06: PERCENTAGE happy path (không cap) → đúng phần trăm
 *   - TC-PROMO-07: FIXED_AMOUNT > orderAmount → cap bằng orderAmount
 */
@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock private PromotionRepository promotionRepository;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks private PromotionService promotionService;

    private static final String CODE = "SUMMER2026";
    private static final BigDecimal CART_TOTAL = new BigDecimal("10000000"); // 10M VND

    private Promotion percentageVoucher(BigDecimal value, BigDecimal maxDiscount,
                                       BigDecimal minOrder, Integer usageLimit, Integer usedCount,
                                       Instant start, Instant end, PromotionStatus status) {
        return Promotion.builder()
                .id("promo-1")
                .code(CODE)
                .type(PromotionType.PERCENTAGE)
                .value(value)
                .minOrderAmount(minOrder)
                .maxDiscountAmount(maxDiscount)
                .startDate(start)
                .endDate(end)
                .usageLimit(usageLimit)
                .usedCount(usedCount)
                .status(status)
                .build();
    }

    // ============================================================
    // TC-PROMO-01 — voucher hết hạn
    // ============================================================
    @Test
    @DisplayName("validateAndCalculate khi voucher đã hết hạn (endDate < now) - valid=false, message rõ")
    void validate_ExpiredVoucher_ReturnsInvalid() {
        Instant past = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        Promotion expired = percentageVoucher(
                new BigDecimal("10"), null, BigDecimal.ZERO,
                null, 0, past, yesterday, PromotionStatus.ACTIVE);

        when(promotionRepository.findByCodeIgnoreCase(CODE)).thenReturn(Optional.of(expired));

        ValidatePromotionResponse resp = promotionService.validateAndCalculate(CODE, CART_TOTAL);

        assertFalse(resp.isValid());
        assertEquals(BigDecimal.ZERO, resp.getDiscountAmount());
        assertTrue(resp.getMessage().contains("hết hạn"),
                "Expected message to mention 'hết hạn', got: " + resp.getMessage());
    }

    // ============================================================
    // TC-PROMO-02 — PERCENTAGE cap ở maxDiscountAmount
    // 10% of 10M = 1M, but cap = 500k → result = 500k
    // ============================================================
    @Test
    @DisplayName("PERCENTAGE 10%, max=500k, cart=10M - discount = 500k (bị cap)")
    void validate_PercentageWithCap_RespectsMaxDiscount() {
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
        Promotion p = percentageVoucher(
                new BigDecimal("10"),                  // 10%
                new BigDecimal("500000"),              // max 500k
                BigDecimal.ZERO,
                null, 0, past, future, PromotionStatus.ACTIVE);

        when(promotionRepository.findByCodeIgnoreCase(CODE)).thenReturn(Optional.of(p));

        ValidatePromotionResponse resp = promotionService.validateAndCalculate(CODE, CART_TOTAL);

        assertTrue(resp.isValid());
        assertEquals(0, new BigDecimal("500000").compareTo(resp.getDiscountAmount()),
                "Expected discount = 500000, got " + resp.getDiscountAmount());
    }

    // ============================================================
    // TC-PROMO-03 — minOrderAmount không thoả
    // ============================================================
    @Test
    @DisplayName("minOrderAmount=1M, cart=500k - valid=false, message nói về tối thiểu")
    void validate_BelowMinOrder_ReturnsInvalid() {
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
        Promotion p = percentageVoucher(
                new BigDecimal("10"), null,
                new BigDecimal("1000000"),             // min 1M
                null, 0, past, future, PromotionStatus.ACTIVE);

        when(promotionRepository.findByCodeIgnoreCase(CODE)).thenReturn(Optional.of(p));

        BigDecimal smallCart = new BigDecimal("500000");
        ValidatePromotionResponse resp = promotionService.validateAndCalculate(CODE, smallCart);

        assertFalse(resp.isValid());
        assertTrue(resp.getMessage().toLowerCase().contains("tối thiểu")
                || resp.getMessage().toLowerCase().contains("minimum"));
    }

    // ============================================================
    // TC-PROMO-04 — code không tồn tại
    // ============================================================
    @Test
    @DisplayName("Code không tồn tại trong DB - valid=false, không throw")
    void validate_UnknownCode_ReturnsInvalid() {
        when(promotionRepository.findByCodeIgnoreCase(CODE)).thenReturn(Optional.empty());

        ValidatePromotionResponse resp = promotionService.validateAndCalculate(CODE, CART_TOTAL);

        assertFalse(resp.isValid());
        assertTrue(resp.getMessage().toLowerCase().contains("không tồn tại"));
    }

    // ============================================================
    // TC-PROMO-05 — usedCount >= usageLimit
    // ============================================================
    @Test
    @DisplayName("usedCount(100) >= usageLimit(100) - valid=false, hết lượt")
    void validate_UsageExhausted_ReturnsInvalid() {
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
        Promotion p = percentageVoucher(
                new BigDecimal("10"), null, BigDecimal.ZERO,
                100, 100,                                 // limit 100, used 100
                past, future, PromotionStatus.ACTIVE);

        when(promotionRepository.findByCodeIgnoreCase(CODE)).thenReturn(Optional.of(p));

        ValidatePromotionResponse resp = promotionService.validateAndCalculate(CODE, CART_TOTAL);

        assertFalse(resp.isValid());
        assertTrue(resp.getMessage().toLowerCase().contains("hết lượt")
                || resp.getMessage().toLowerCase().contains("usage"));
    }

    // ============================================================
    // TC-PROMO-06 — PERCENTAGE happy path (không cap)
    // 10% of 1M = 100k, không cap
    // ============================================================
    @Test
    @DisplayName("PERCENTAGE 10%, không cap, cart=1M - discount = 100k")
    void validate_PercentageNoCap_CorrectAmount() {
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
        Promotion p = percentageVoucher(
                new BigDecimal("10"),
                null,                                    // no cap
                BigDecimal.ZERO,
                null, 0, past, future, PromotionStatus.ACTIVE);

        when(promotionRepository.findByCodeIgnoreCase(CODE)).thenReturn(Optional.of(p));

        BigDecimal cart = new BigDecimal("1000000");
        ValidatePromotionResponse resp = promotionService.validateAndCalculate(CODE, cart);

        assertTrue(resp.isValid());
        assertEquals(0, new BigDecimal("100000").compareTo(resp.getDiscountAmount()),
                "Expected 100000, got " + resp.getDiscountAmount());
    }

    // ============================================================
    // TC-PROMO-07 — FIXED_AMOUNT vượt quá orderAmount → cap
    // ============================================================
    @Test
    @DisplayName("FIXED_AMOUNT=500k, cart=300k - discount = 300k (không vượt quá order)")
    void validate_FixedAmountCappedAtOrder() {
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
        Promotion p = Promotion.builder()
                .id("promo-2")
                .code(CODE)
                .type(PromotionType.FIXED_AMOUNT)
                .value(new BigDecimal("500000"))
                .minOrderAmount(BigDecimal.ZERO)
                .startDate(past)
                .endDate(future)
                .usedCount(0)
                .status(PromotionStatus.ACTIVE)
                .build();

        when(promotionRepository.findByCodeIgnoreCase(CODE)).thenReturn(Optional.of(p));

        BigDecimal cart = new BigDecimal("300000");
        ValidatePromotionResponse resp = promotionService.validateAndCalculate(CODE, cart);

        assertTrue(resp.isValid());
        assertEquals(0, new BigDecimal("300000").compareTo(resp.getDiscountAmount()),
                "Discount must not exceed orderAmount; got " + resp.getDiscountAmount());
    }

    // ============================================================
    // TC-PROMO-08 — status != ACTIVE
    // ============================================================
    @Test
    @DisplayName("status=DISABLED - valid=false ngay cả khi dates hợp lệ")
    void validate_DisabledStatus_ReturnsInvalid() {
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
        Promotion p = percentageVoucher(
                new BigDecimal("10"), null, BigDecimal.ZERO,
                null, 0, past, future, PromotionStatus.DISABLED);

        when(promotionRepository.findByCodeIgnoreCase(CODE)).thenReturn(Optional.of(p));

        ValidatePromotionResponse resp = promotionService.validateAndCalculate(CODE, CART_TOTAL);

        assertFalse(resp.isValid());
        assertTrue(resp.getMessage().toLowerCase().contains("vô hiệu")
                || resp.getMessage().toLowerCase().contains("disabled"));
    }

    // ============================================================
    // TC-PROMO-08b — status EXPIRED (spec Mục 3.3 enum: ACTIVE|EXPIRED|DISABLED)
    // ============================================================
    @Test
    @DisplayName("status=EXPIRED - valid=false với message rõ ràng")
    void validate_ExpiredStatus_ReturnsInvalid() {
        // endDate is still in future, but status was manually flipped to EXPIRED
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
        Promotion p = percentageVoucher(
                new BigDecimal("10"), null, BigDecimal.ZERO,
                null, 0, past, future, PromotionStatus.EXPIRED);

        when(promotionRepository.findByCodeIgnoreCase(CODE)).thenReturn(Optional.of(p));

        ValidatePromotionResponse resp = promotionService.validateAndCalculate(CODE, CART_TOTAL);

        assertFalse(resp.isValid());
        // Status check fires before date check, so the message reflects status
        assertTrue(resp.getMessage().toLowerCase().contains("vô hiệu")
                || resp.getMessage().toLowerCase().contains("hết hạn")
                || resp.getMessage().toLowerCase().contains("expired"));
    }

    // ============================================================
    // TC-PROMO-09 — incrementUsage atomic
    // ============================================================
    @Test
    @DisplayName("incrementUsage gọi findAndModify, không throw khi code tồn tại")
    void incrementUsage_ExistingCode_Succeeds() {
        Promotion p = Promotion.builder().code(CODE).usedCount(5).build();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(Promotion.class)))
                .thenReturn(p);

        assertDoesNotThrow(() -> promotionService.incrementUsage(CODE));

        // Verify the update was an $inc on usedCount
        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), captor.capture(), eq(Promotion.class));
        String updateJson = captor.getValue().getUpdateObject().toJson();
        assertTrue(updateJson.contains("usedCount"));
    }

    // ============================================================
    // TC-PROMO-10 — incrementUsage code không tồn tại
    // ============================================================
    @Test
    @DisplayName("incrementUsage với code không tồn tại - throw EntityNotFoundException")
    void incrementUsage_UnknownCode_Throws() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(Promotion.class)))
                .thenReturn(null);

        assertThrows(EntityNotFoundException.class,
                () -> promotionService.incrementUsage(CODE));
    }
}
