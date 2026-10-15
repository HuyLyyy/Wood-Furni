package com.woodfurni.delivery.config;

import com.woodfurni.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Chạy 1 lần duy nhất khi app start — sửa dữ liệu delivery bị corrupted
 * do bug trong các commit trước:
 *
 * 1. Trip status bị finalize sai (trip RESCHEDULED nhưng lưu COMPLETED)
 * 2. nextDeliveryDate bị Instant gần epoch 0 (bug timezone frontend cũ)
 * 3. nextDeliveryDate bị lệch ±1 ngày do .toISOString() convert sai
 *
 * Sử dụng flag file (marker) để đảm bảo chỉ chạy 1 lần, tránh re-fix dữ
 * liệu đã được sửa. Có thể xóa flag file để force re-run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryDataFixRunner implements CommandLineRunner {

    private final DeliveryService deliveryService;

    /** Flag file đánh dấu đã fix xong. Có thể xóa để force re-run. */
    private static final String FIXED_FLAG_FILE = System.getProperty("java.io.tmpdir")
            + "/.woodfurni-delivery-data-fixed-v2";

    @Override
    public void run(String... args) {
        File flag = new File(FIXED_FLAG_FILE);
        if (flag.exists()) {
            log.info("[DeliveryDataFix] Already ran — skipping. (Xóa {} để re-run)", FIXED_FLAG_FILE);
            return;
        }

        log.warn("[DeliveryDataFix] ============================================");
        log.warn("[DeliveryDataFix] Running ONE-TIME delivery data migration");
        log.warn("[DeliveryDataFix] ============================================");

        try {
            // 1. Fix trip statuses (trip COMPLETED sai → RESCHEDULED)
            int tripFixed = deliveryService.recomputeAllTripStatuses("system-migration");
            log.info("[DeliveryDataFix] Trip status recomputed: {} trips changed", tripFixed);

            // 2. Fix corrupted nextDeliveryDate (epoch 0 / 1970 / 1900)
            int dateFixed = deliveryService.fixCorruptedNextDeliveryDates("system-migration");
            log.info("[DeliveryDataFix] Corrupted nextDeliveryDate fixed: {} entries", dateFixed);

            // 3. Mark as done
            boolean created = flag.createNewFile();
            log.warn("[DeliveryDataFix] DONE. Trips: {}, Dates: {}. Flag created: {}",
                    tripFixed, dateFixed, created);
            log.warn("[DeliveryDataFix] To re-run: delete {}", FIXED_FLAG_FILE);

        } catch (Exception e) {
            log.error("[DeliveryDataFix] FAILED — data not fully fixed. Will retry next startup.",
                    e);
        }
    }
}
