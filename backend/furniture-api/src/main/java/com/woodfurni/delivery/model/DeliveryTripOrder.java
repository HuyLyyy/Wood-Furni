package com.woodfurni.delivery.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Join collection giữa DeliveryTrip và Order — "delivery_trip_orders".
 *
 * Một đơn hàng chỉ được gán cho đúng 1 chuyến xe tại 1 thời điểm; compound
 * unique index trên (tripId, orderId) chống trùng.
 *
 * Sequence lưu thứ tự đơn trong chuyến (1, 2, 3…) để hiển thị và in phiếu giao.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "delivery_trip_orders")
@CompoundIndexes({
        @CompoundIndex(name = "trip_order_unique_idx", def = "{'tripId': 1, 'orderId': 1}", unique = true)
})
public class DeliveryTripOrder {

    @Id
    private String id;

    @Indexed
    private String tripId;

    @Indexed
    private String orderId;

    /** Số thứ tự giao trong chuyến (1-based). */
    private int sequence;

    /** Snapshot kích thước / tải trọng / thể tích của đơn tại lúc gán. */
    private double weightKg;
    private double volumeM3;
    private double lengthCm;
    private double widthCm;
    private double heightCm;

    private Instant addedAt;
}
