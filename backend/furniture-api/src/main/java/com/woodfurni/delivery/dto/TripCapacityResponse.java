package com.woodfurni.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả validate capacity giữa tổng đơn và loại xe.
 *
 * {@code fits = true}  → có thể tạo chuyến.
 * {@code fits = false} → kèm {@link #reasons} giải thích vì sao không lọt
 * (vd vượt tải trọng, vượt thể tích, đơn có kích thước kiện lớn hơn thùng xe).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCapacityResponse {
    private boolean fits;
    private int totalOrders;
    private double totalWeightKg;
    private double totalVolumeM3;
    private double maxLengthCm;
    private double maxWidthCm;
    private double maxHeightCm;
    private VehicleTypeResponse vehicle;
    /** Các nguyên nhân khiến không lọt; rỗng nếu fits = true. */
    private List<String> reasons = new ArrayList<>();
}
