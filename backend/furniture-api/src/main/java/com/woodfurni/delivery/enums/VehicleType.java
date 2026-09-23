package com.woodfurni.delivery.enums;

/**
 * Loại xe vận chuyển — bảng capacity cố định cho module Chuyến xe.
 *
 * Mỗi loại xe có:
 *   - {@code minWeightKg} / {@code maxWeightKg} : khoảng tải trọng (kg)
 *   - {@code maxVolumeM3}                      : thể tích chứa tối đa (m³)
 *   - {@code maxLengthCm} / {@code maxWidthCm} / {@code maxHeightCm}
 *                                              : kích thước đơn hàng lớn nhất
 *                                                chứa được trong thùng xe (cm)
 *
 * Số liệu dùng để backend validate khi staff tạo chuyến xe. Các giá trị là
 * ước lượng hợp lý cho xe tải nội thành; có thể tinh chỉnh theo xe thật
 * của WOOD-FURNI khi cần.
 */
public enum VehicleType {
    /** Xe van nhỏ: 500 kg – 1 tấn. */
    VAN_SMALL(
            "Xe van nhỏ",
            500.0, 1000.0,
            8.0,
            250.0, 160.0, 150.0),
    /** Xe tải nhẹ: 1 – 1.5 tấn. */
    TRUCK_LIGHT(
            "Xe tải nhẹ",
            1000.0, 1500.0,
            14.0,
            320.0, 180.0, 180.0),
    /** Xe tải trung: 2.5 – 5 tấn. */
    TRUCK_MEDIUM(
            "Xe tải trung",
            2500.0, 5000.0,
            28.0,
            500.0, 220.0, 220.0);

    private final String displayName;
    private final double minWeightKg;
    private final double maxWeightKg;
    private final double maxVolumeM3;
    private final double maxLengthCm;
    private final double maxWidthCm;
    private final double maxHeightCm;

    VehicleType(String displayName,
                double minWeightKg, double maxWeightKg,
                double maxVolumeM3,
                double maxLengthCm, double maxWidthCm, double maxHeightCm) {
        this.displayName = displayName;
        this.minWeightKg = minWeightKg;
        this.maxWeightKg = maxWeightKg;
        this.maxVolumeM3 = maxVolumeM3;
        this.maxLengthCm = maxLengthCm;
        this.maxWidthCm = maxWidthCm;
        this.maxHeightCm = maxHeightCm;
    }

    public String getDisplayName() { return displayName; }
    public double getMinWeightKg() { return minWeightKg; }
    public double getMaxWeightKg() { return maxWeightKg; }
    public double getMaxVolumeM3() { return maxVolumeM3; }
    public double getMaxLengthCm() { return maxLengthCm; }
    public double getMaxWidthCm() { return maxWidthCm; }
    public double getMaxHeightCm() { return maxHeightCm; }

    /**
     * Lookup by name (case-insensitive). Returns null if not found.
     */
    public static VehicleType fromCode(String code) {
        if (code == null) return null;
        for (VehicleType v : values()) {
            if (v.name().equalsIgnoreCase(code.trim())) return v;
        }
        return null;
    }
}
