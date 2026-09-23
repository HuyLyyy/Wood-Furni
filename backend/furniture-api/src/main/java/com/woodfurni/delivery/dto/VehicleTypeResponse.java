package com.woodfurni.delivery.dto;

import com.woodfurni.delivery.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output DTO mô tả 1 loại xe + capacity tương ứng.
 * Dùng cho dropdown chọn xe trên frontend + capacity preview.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleTypeResponse {
    private String code;
    private String displayName;
    private double minWeightKg;
    private double maxWeightKg;
    private double maxVolumeM3;
    private double maxLengthCm;
    private double maxWidthCm;
    private double maxHeightCm;

    public static VehicleTypeResponse from(VehicleType v) {
        return VehicleTypeResponse.builder()
                .code(v.name())
                .displayName(v.getDisplayName())
                .minWeightKg(v.getMinWeightKg())
                .maxWeightKg(v.getMaxWeightKg())
                .maxVolumeM3(v.getMaxVolumeM3())
                .maxLengthCm(v.getMaxLengthCm())
                .maxWidthCm(v.getMaxWidthCm())
                .maxHeightCm(v.getMaxHeightCm())
                .build();
    }
}
