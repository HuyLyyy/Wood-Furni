package com.woodfurni.shipping.service;

import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.shipping.dto.ShippingCalculateRequest;
import com.woodfurni.shipping.dto.ShippingCalculateResponse;
import com.woodfurni.shipping.dto.ShippingDistanceRequest;
import com.woodfurni.shipping.model.ShippingDistance;
import com.woodfurni.shipping.repository.ShippingDistanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Shipping service — distance table lookup and fee calculation.
 *
 * Fee formula:
 *
 *   distanceKm <= base-radius-km  →  fee = base-fee
 *   distanceKm >  base-radius-km  →  fee = base-fee
 *                                         + ceil(distanceKm - base-radius-km) * surcharge-per-km
 *
 * Lookup rules:
 *
 *   a. If city is "Hồ Chí Minh":
 *        - Look up the (city, district) pair in the shippingdistances collection.
 *        - If not found → throw clear error "Khu vực chưa được hỗ trợ tính phí ship".
 *   b. If city is anything else:
 *        - Use {@code out-of-province-distance-km} as the distance (ignore table).
 *        - Mark isOutOfProvince = true in the response.
 *
 * Configuration keys (application.yml):
 *   shipping.base-fee                  = 100,000 VND  (fee within base-radius-km)
 *   shipping.base-radius-km            = 10 km
 *   shipping.surcharge-per-km         = 6,000 VND / extra km
 *   shipping.out-of-province-distance-km = 60 km  (fallback for non-HCM cities)
 *
 * Numeric examples:
 *   distanceKm = 10  → fee = 100,000
 *   distanceKm = 13  → extraKm = ceil(13-10) = 3  → fee = 100,000 + 3*6,000 = 118,000
 *   distanceKm = 13.2→ extraKm = ceil(3.2) = 4    → fee = 100,000 + 4*6,000 = 124,000
 *   city ≠ HCM       → distanceKm = 60             → extraKm = ceil(60-10) = 50 → fee = 400,000
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingService {

    private static final String HCM_CITY = "Hồ Chí Minh";

    private final ShippingDistanceRepository distanceRepository;

    // ─── Config ────────────────────────────────────────────────────────────────

    /** Base shipping fee (VND) — charged for distances up to base-radius-km. */
    @Value("${shipping.base-fee:100000}")
    private BigDecimal baseFee;

    /** Radius (km) within which only the base fee is charged. */
    @Value("${shipping.base-radius-km:10}")
    private BigDecimal baseRadiusKm;

    /** Surcharge per extra kilometre beyond base-radius-km. */
    @Value("${shipping.surcharge-per-km:6000}")
    private BigDecimal surchargePerKm;

    /** Fallback distance (km) for cities outside Hồ Chí Minh. */
    @Value("${shipping.out-of-province-distance-km:60}")
    private BigDecimal outOfProvinceDistanceKm;

    // ─── Distance CRUD (ADMIN) ─────────────────────────────────────────────────

    /**
     * List all distance records, newest first by id.
     */
    public List<ShippingDistance> getAllDistances() {
        return distanceRepository.findAll();
    }

    /**
     * Create a new distance record.
     *
     * @throws DuplicateKeyException if the (city, district) pair already exists
     */
    public ShippingDistance createDistance(ShippingDistanceRequest req) {
        ShippingDistance entity = ShippingDistanceRequest.toEntity(req);
        ShippingDistance saved = distanceRepository.save(entity);
        log.info("[Shipping] Created distance id={} {} / {} → {}km",
                saved.getId(), saved.getCity(), saved.getDistrict(), saved.getDistanceKm());
        return saved;
    }

    /**
     * Update an existing distance record by id.
     *
     * @throws EntityNotFoundException if the id does not exist
     */
    public ShippingDistance updateDistance(String id, ShippingDistanceRequest req) {
        ShippingDistance existing = distanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Distance record not found: " + id));

        existing.setCity(req.getCity());
        existing.setDistrict(req.getDistrict());
        existing.setDistanceKm(req.getDistanceKm());

        ShippingDistance saved = distanceRepository.save(existing);
        log.info("[Shipping] Updated distance id={} {} / {} → {}km",
                saved.getId(), saved.getCity(), saved.getDistrict(), saved.getDistanceKm());
        return saved;
    }

    // ─── Fee calculation (CUSTOMER / checkout preview) ─────────────────────────

    /**
     * Calculate shipping fee from a delivery address.
     *
     * This is the primary customer-facing entry point. It looks up the distance
     * table for Hồ Chí Minh addresses, falls back to the configured province
     * distance for all other cities, then applies the fee formula.
     *
     * @param city    delivery city name
     * @param district delivery district name
     * @return fee result with distance and isOutOfProvince flag
     */
    public ShippingCalculateResponse calculateFee(String city, String district) {
        BigDecimal distanceKm;
        boolean isOutOfProvince;

        if (HCM_CITY.equalsIgnoreCase(city)) {
            // Step 3a: look up (city, district) in the table.
            ShippingDistance record = distanceRepository
                    .findByCityAndDistrict(city, district)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Khu vực chưa được hỗ trợ tính phí ship"));

            distanceKm = record.getDistanceKm();
            isOutOfProvince = false;
        } else {
            // Step 3b: non-HCM → use the configured fallback distance.
            distanceKm = outOfProvinceDistanceKm;
            isOutOfProvince = true;
        }

        // Step 3c: apply the fee formula.
        BigDecimal fee = applyFormula(distanceKm);

        log.info("[Shipping] Calculated fee: {} / {} → distance={}km, isOutOfProvince={}, fee={}VND",
                city, district, distanceKm, isOutOfProvince, fee);

        return ShippingCalculateResponse.builder()
                .fee(fee)
                .distanceKm(distanceKm)
                .isOutOfProvince(isOutOfProvince)
                .build();
    }

    /**
     * Calculate fee from a request DTO (used directly by the controller).
     */
    public ShippingCalculateResponse calculateFee(ShippingCalculateRequest request) {
        return calculateFee(request.getCity(), request.getDistrict());
    }

    // ─── Fee formula ──────────────────────────────────────────────────────────

    /**
     * Apply the fee formula:
     *
     *   distanceKm <= base-radius-km  →  fee = base-fee
     *   distanceKm >  base-radius-km  →  fee = base-fee
     *                                         + ceil(distanceKm - base-radius-km) * surcharge-per-km
     *
     * The ceiling is applied to the distance delta, not the final fee.
     *
     * @param distanceKm the effective distance for this delivery
     * @return fee in VND
     */
    BigDecimal applyFormula(BigDecimal distanceKm) {
        if (distanceKm == null) {
            distanceKm = BigDecimal.ZERO;
        }

        if (distanceKm.compareTo(baseRadiusKm) <= 0) {
            // Within the base radius — flat base fee.
            return baseFee;
        }

        // Extra kilometres beyond the base radius, rounded UP to the next whole km.
        BigDecimal extraKmRaw = distanceKm.subtract(baseRadiusKm);
        BigDecimal extraKm = extraKmRaw.setScale(0, RoundingMode.CEILING); // ceil
        BigDecimal surcharge = extraKm.multiply(surchargePerKm);

        return baseFee.add(surcharge);
    }
}
