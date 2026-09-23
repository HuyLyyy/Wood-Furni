package com.woodfurni.delivery.dto;

import com.woodfurni.delivery.enums.DeliveryTripStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Response chi tiết chuyến xe + danh sách đơn gán vào.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryTripResponse {
    private String id;
    private String tripNumber;
    private DeliveryTripStatus status;
    private VehicleTypeResponse vehicle;
    private String driverId;
    private String driverName;
    private String driverPhone;
    private int totalOrders;
    private double totalWeightKg;
    private double totalVolumeM3;
    private double maxLengthCm;
    private double maxWidthCm;
    private double maxHeightCm;
    private String note;
    private String createdBy;
    private Instant createdAt;
    private Instant shippedAt;
    private Instant completedAt;
    /** Danh sách đơn thuộc chuyến (rỗng trong list ngắn, đầy đủ trong detail). */
    @Builder.Default
    private List<TripOrderResponse> orders = new ArrayList<>();
}
