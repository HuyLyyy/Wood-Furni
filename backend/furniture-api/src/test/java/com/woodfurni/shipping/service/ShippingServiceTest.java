package com.woodfurni.shipping.service;

import com.woodfurni.shipping.dto.ShippingCalculateRequest;
import com.woodfurni.shipping.dto.ShippingCalculateResponse;
import com.woodfurni.shipping.model.ShippingDistance;
import com.woodfurni.shipping.repository.ShippingDistanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.anyString;

/**
 * Unit tests for ShippingService — distance lookup + fee formula.
 *
 * Spec examples (must all pass):
 *
 *   distanceKm = 10   → fee = 100,000
 *   distanceKm = 13   → extraKm = ceil(13-10) = 3  → fee = 100,000 + 3*6,000 = 118,000
 *   distanceKm = 13.2 → extraKm = ceil(3.2) = 4    → fee = 100,000 + 4*6,000 = 124,000
 *   city ≠ HCM        → distanceKm = 60               → extraKm = ceil(60-10) = 50 → fee = 400,000
 *   district not in table (HCM) → throw IllegalArgumentException
 */
@ExtendWith(MockitoExtension.class)
class ShippingServiceTest {

    private static final String HCM = "Hồ Chí Minh";

    @Mock
    private ShippingDistanceRepository distanceRepository;

    @InjectMocks
    private ShippingService shippingService;

    @BeforeEach
    void setUp() {
        // Inject @Value fields via reflection (no Spring context).
        ReflectionTestUtils.setField(shippingService, "baseFee", new BigDecimal("100000"));
        ReflectionTestUtils.setField(shippingService, "baseRadiusKm", new BigDecimal("10"));
        ReflectionTestUtils.setField(shippingService, "surchargePerKm", new BigDecimal("6000"));
        ReflectionTestUtils.setField(shippingService, "outOfProvinceDistanceKm", new BigDecimal("60"));
    }

    // ========================================================================
    // applyFormula — direct formula tests (no DB)
    // ========================================================================

    @Nested
    @DisplayName("applyFormula()")
    class ApplyFormulaTests {

        @Test
        @DisplayName("distanceKm = 10 (boundary) → fee = baseFee = 100,000")
        void distanceExactlyAtBaseRadius_returnsBaseFee() {
            BigDecimal fee = shippingService.applyFormula(new BigDecimal("10"));
            assertEquals(new BigDecimal("100000"), fee);
        }

        @Test
        @DisplayName("distanceKm = 9 (< base-radius) → fee = baseFee = 100,000")
        void distanceBelowBaseRadius_returnsBaseFee() {
            BigDecimal fee = shippingService.applyFormula(new BigDecimal("9"));
            assertEquals(new BigDecimal("100000"), fee);
        }

        @Test
        @DisplayName("distanceKm = 0 (kho) → fee = baseFee = 100,000")
        void distanceZero_returnsBaseFee() {
            BigDecimal fee = shippingService.applyFormula(BigDecimal.ZERO);
            assertEquals(new BigDecimal("100000"), fee);
        }

        @Test
        @DisplayName("distanceKm = 13 → extraKm = ceil(3) = 3 → fee = 100,000 + 3*6,000 = 118,000")
        void distance13km_extraKm3_fee118000() {
            BigDecimal fee = shippingService.applyFormula(new BigDecimal("13"));
            assertEquals(new BigDecimal("118000"), fee);
        }

        @Test
        @DisplayName("distanceKm = 13.2 → extraKm = ceil(3.2) = 4 → fee = 100,000 + 4*6,000 = 124,000")
        void distance13_2km_extraKm4_fee124000() {
            BigDecimal fee = shippingService.applyFormula(new BigDecimal("13.2"));
            assertEquals(new BigDecimal("124000"), fee);
        }

        @Test
        @DisplayName("distanceKm = 11 → extraKm = ceil(1) = 1 → fee = 106,000")
        void distance11km_extraKm1_fee106000() {
            BigDecimal fee = shippingService.applyFormula(new BigDecimal("11"));
            assertEquals(new BigDecimal("106000"), fee);
        }

        @Test
        @DisplayName("distanceKm = 60 (out-of-province fallback) → extraKm = ceil(50) = 50 → fee = 400,000")
        void distance60km_extraKm50_fee400000() {
            BigDecimal fee = shippingService.applyFormula(new BigDecimal("60"));
            assertEquals(new BigDecimal("400000"), fee);
        }

        @Test
        @DisplayName("distanceKm = null → treated as 0 → fee = baseFee")
        void distanceNull_treatedAsZero() {
            BigDecimal fee = shippingService.applyFormula(null);
            assertEquals(new BigDecimal("100000"), fee);
        }

        @ParameterizedTest
        @CsvSource({
            "1,   100000",
            "5,   100000",
            "10,  100000",
            "11,  106000",
            "12,  112000",
            "12,  112000",
            "13,  118000",
            "13.1,124000",
            "13.5,124000",
            "13.9,124000",
            "14,  124000",
            "14,  124000",
            "15,  130000",
            "16,  136000",
            "17,  142000",
            "18,  148000",
            "20,  160000",
            "35,  250000",
            "50,  340000",
            "60,  400000",
            "100, 640000"
        })
        @DisplayName("Parametrized: distance → expected fee")
        void parametrized(String distanceStr, String expectedFeeStr) {
            BigDecimal fee = shippingService.applyFormula(new BigDecimal(distanceStr));
            assertEquals(new BigDecimal(expectedFeeStr), fee);
        }
    }

    // ========================================================================
    // calculateFee(city, district) — integration with repository
    // ========================================================================

    @Nested
    @DisplayName("calculateFee(city, district)")
    class CalculateFeeTests {

        @Test
        @DisplayName("HCM + Quận 1 (8km) → fee = 100,000 (within base radius)")
        void hcmQuan1_withinBaseRadius() {
            ShippingDistance dist = distance("Quận 1", "8");
            when(distanceRepository.findByCityAndDistrict(HCM, "Quận 1"))
                    .thenReturn(Optional.of(dist));

            ShippingCalculateResponse result = shippingService.calculateFee(HCM, "Quận 1");

            assertEquals(new BigDecimal("100000"), result.getFee());
            assertEquals(new BigDecimal("8"), result.getDistanceKm());
            assertFalse(result.getIsOutOfProvince());
        }

        @Test
        @DisplayName("HCM + Quận 8 (0km, kho) → fee = 100,000")
        void hcmQuan8_kho() {
            ShippingDistance dist = distance("Quận 8", "0");
            when(distanceRepository.findByCityAndDistrict(HCM, "Quận 8"))
                    .thenReturn(Optional.of(dist));

            ShippingCalculateResponse result = shippingService.calculateFee(HCM, "Quận 8");

            assertEquals(new BigDecimal("100000"), result.getFee());
            assertEquals(BigDecimal.ZERO, result.getDistanceKm());
            assertFalse(result.getIsOutOfProvince());
        }

        @Test
        @DisplayName("HCM + Thủ Đức (18km) → extraKm = ceil(8) = 8 → fee = 148,000")
        void hcmThuDuc_18km() {
            ShippingDistance dist = distance("Thủ Đức", "18");
            when(distanceRepository.findByCityAndDistrict(HCM, "Thủ Đức"))
                    .thenReturn(Optional.of(dist));

            ShippingCalculateResponse result = shippingService.calculateFee(HCM, "Thủ Đức");

            assertEquals(new BigDecimal("148000"), result.getFee());
            assertEquals(new BigDecimal("18"), result.getDistanceKm());
            assertFalse(result.getIsOutOfProvince());
        }

        @Test
        @DisplayName("HCM + Củ Chi (35km) → extraKm = ceil(25) = 25 → fee = 250,000")
        void hcmCuChi_35km() {
            ShippingDistance dist = distance("Củ Chi", "35");
            when(distanceRepository.findByCityAndDistrict(HCM, "Củ Chi"))
                    .thenReturn(Optional.of(dist));

            ShippingCalculateResponse result = shippingService.calculateFee(HCM, "Củ Chi");

            assertEquals(new BigDecimal("250000"), result.getFee());
            assertEquals(new BigDecimal("35"), result.getDistanceKm());
            assertFalse(result.getIsOutOfProvince());
        }

        @Test
        @DisplayName("HCM + Cần Giờ (35km) → extraKm = ceil(25) = 25 → fee = 250,000")
        void hcmCanGio_35km() {
            ShippingDistance dist = distance("Cần Giờ", "35");
            when(distanceRepository.findByCityAndDistrict(HCM, "Cần Giờ"))
                    .thenReturn(Optional.of(dist));

            ShippingCalculateResponse result = shippingService.calculateFee(HCM, "Cần Giờ");

            assertEquals(new BigDecimal("250000"), result.getFee());
            assertEquals(new BigDecimal("35"), result.getDistanceKm());
            assertFalse(result.getIsOutOfProvince());
        }

        @Test
        @DisplayName("HCM (case-insensitive) + Quận 7 → fee = 100,000")
        void hcmCaseInsensitive() {
            // Service uses equalsIgnoreCase for the HCM check, then passes the raw city
            // string directly to the repository. Stub with anyString() to avoid strict-mismatch.
            ShippingDistance dist = distance("Quận 7", "9");
            when(distanceRepository.findByCityAndDistrict(anyString(), eq("Quận 7")))
                    .thenReturn(Optional.of(dist));

            ShippingCalculateResponse result = shippingService.calculateFee("hồ chí minh", "Quận 7");

            assertEquals(new BigDecimal("100000"), result.getFee());
            assertFalse(result.getIsOutOfProvince());
        }

        @Test
        @DisplayName("HCM + district not in table → throw clear error")
        void hcmDistrictNotInTable_throws() {
            when(distanceRepository.findByCityAndDistrict(HCM, "Quận 99"))
                    .thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> shippingService.calculateFee(HCM, "Quận 99"));

            assertEquals("Khu vực chưa được hỗ trợ tính phí ship", ex.getMessage());
        }

        @Test
        @DisplayName("City = 'Hà Nội' (≠ HCM) → uses fallback 60km → fee = 400,000, isOutOfProvince = true")
        void nonHcmCity_usesFallbackDistance() {
            ShippingCalculateResponse result = shippingService.calculateFee("Hà Nội", "Quận Ba Đình");

            assertEquals(new BigDecimal("400000"), result.getFee());
            assertEquals(new BigDecimal("60"), result.getDistanceKm());
            assertTrue(result.getIsOutOfProvince());
        }

        @Test
        @DisplayName("City = 'Đà Nẵng' → uses fallback 60km → fee = 400,000, isOutOfProvince = true")
        void danang_usesFallbackDistance() {
            ShippingCalculateResponse result = shippingService.calculateFee("Đà Nẵng", "Quận Hải Châu");

            assertEquals(new BigDecimal("400000"), result.getFee());
            assertEquals(new BigDecimal("60"), result.getDistanceKm());
            assertTrue(result.getIsOutOfProvince());
        }

        @Test
        @DisplayName("City = 'Hồ Chí Minh' + Bình Chánh (9km) → fee = 100,000")
        void binhChanh_9km() {
            ShippingDistance dist = distance("Bình Chánh", "9");
            when(distanceRepository.findByCityAndDistrict(HCM, "Bình Chánh"))
                    .thenReturn(Optional.of(dist));

            ShippingCalculateResponse result = shippingService.calculateFee(HCM, "Bình Chánh");

            assertEquals(new BigDecimal("100000"), result.getFee());
            assertEquals(new BigDecimal("9"), result.getDistanceKm());
            assertFalse(result.getIsOutOfProvince());
        }

        @Test
        @DisplayName("City = 'Hồ Chí Minh' + Quận 10 (12km) → extraKm = ceil(2) = 2 → fee = 112,000")
        void quan10_12km() {
            ShippingDistance dist = distance("Quận 10", "12");
            when(distanceRepository.findByCityAndDistrict(HCM, "Quận 10"))
                    .thenReturn(Optional.of(dist));

            ShippingCalculateResponse result = shippingService.calculateFee(HCM, "Quận 10");

            assertEquals(new BigDecimal("112000"), result.getFee());
            assertEquals(new BigDecimal("12"), result.getDistanceKm());
        }

        @Test
        @DisplayName("City = 'Hồ Chí Minh' + Quận 12 (18km) → extraKm = ceil(8) = 8 → fee = 148,000")
        void quan12_18km() {
            ShippingDistance dist = distance("Quận 12", "18");
            when(distanceRepository.findByCityAndDistrict(HCM, "Quận 12"))
                    .thenReturn(Optional.of(dist));

            ShippingCalculateResponse result = shippingService.calculateFee(HCM, "Quận 12");

            assertEquals(new BigDecimal("148000"), result.getFee());
            assertEquals(new BigDecimal("18"), result.getDistanceKm());
        }
    }

    // ========================================================================
    // calculateFee(ShippingCalculateRequest) — DTO overload
    // ========================================================================

    @Nested
    @DisplayName("calculateFee(ShippingCalculateRequest)")
    class CalculateFeeDtoTests {

        @Test
        @DisplayName("Request with HCM city + Quận 7 → fee = 100,000")
        void request_hcmQuan7() {
            ShippingDistance dist = distance("Quận 7", "9");
            when(distanceRepository.findByCityAndDistrict(HCM, "Quận 7"))
                    .thenReturn(Optional.of(dist));

            ShippingCalculateRequest request = ShippingCalculateRequest.builder()
                    .city(HCM)
                    .district("Quận 7")
                    .build();

            ShippingCalculateResponse result = shippingService.calculateFee(request);

            assertEquals(new BigDecimal("100000"), result.getFee());
            assertFalse(result.getIsOutOfProvince());
        }

        @Test
        @DisplayName("Request with non-HCM city → uses fallback 60km")
        void request_nonHcm() {
            ShippingCalculateRequest request = ShippingCalculateRequest.builder()
                    .city("Cần Thơ")
                    .district("Quận Ninh Kiều")
                    .build();

            ShippingCalculateResponse result = shippingService.calculateFee(request);

            assertEquals(new BigDecimal("400000"), result.getFee());
            assertTrue(result.getIsOutOfProvince());
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static ShippingDistance distance(String district, String distanceKm) {
        return ShippingDistance.builder()
                .city(HCM)
                .district(district)
                .distanceKm(new BigDecimal(distanceKm))
                .build();
    }
}
