package com.woodfurni.shipping.bootstrap;

import com.woodfurni.shipping.model.ShippingDistance;
import com.woodfurni.shipping.repository.ShippingDistanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the shippingdistances collection with estimated distances from the
 * warehouse (Quận 8, TP.HCM) to each HCM district.
 *
 * These are reference values only — adjust {@code distanceKm} to match
 * actual delivery routes in your area.
 *
 * Idempotent: skips silently if the collection already has any records.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ShippingBootstrap {

    private static final String CITY = "Hồ Chí Minh";

    private final ShippingDistanceRepository distanceRepository;

    @Bean
    public ApplicationRunner seedShippingDistances() {
        return args -> {
            // Idempotent: skip if already seeded.
            if (distanceRepository.count() > 0) {
                log.info("[ShippingBootstrap] shippingdistances already seeded ({} records exist).",
                        distanceRepository.count());
                return;
            }

            // Khoảng cách ước lượng từ kho (Quận 8) — ghi rõ là tham khảo.
            List<ShippingDistance> seeds = List.of(
                    // Quận 8 (kho)
                    d("Quận 8", 0),

                    // ~5 km
                    d("Quận 5", 5),
                    d("Quận 6", 5),
                    d("Quận 4", 5),

                    // ~8 km
                    d("Quận 1", 8),
                    d("Quận 3", 8),
                    d("Bình Thạnh", 8),

                    // ~9 km
                    d("Quận 7", 9),
                    d("Bình Chánh", 9),

                    // ~12 km
                    d("Quận 10", 12),
                    d("Quận 11", 12),
                    d("Tân Bình", 12),
                    d("Gò Vấp", 12),

                    // ~18 km
                    d("Quận 12", 18),
                    d("Thủ Đức", 18),

                    // ~20 km
                    d("Nhà Bè", 20),
                    d("Hóc Môn", 20),

                    // ~35 km
                    d("Củ Chi", 35),
                    d("Cần Giờ", 35)
            );

            int saved = 0;
            for (ShippingDistance entry : seeds) {
                try {
                    distanceRepository.save(entry);
                    saved++;
                    log.info("[ShippingBootstrap] Seeded {} / {} → {}km",
                            entry.getCity(), entry.getDistrict(), entry.getDistanceKm());
                } catch (DuplicateKeyException ex) {
                    // Unique index on (city, district) prevents real duplicates;
                    // skip gracefully in case of concurrent seed runs.
                    log.warn("[ShippingBootstrap] Skipped duplicate: {} / {}",
                            entry.getCity(), entry.getDistrict());
                }
            }

            log.info("[ShippingBootstrap] Done. Seeded {} distance record(s) for {}.",
                    saved, CITY);
        };
    }

    private static ShippingDistance d(String district, int distanceKm) {
        return ShippingDistance.builder()
                .city(CITY)
                .district(district)
                .distanceKm(new BigDecimal(distanceKm))
                .build();
    }
}
