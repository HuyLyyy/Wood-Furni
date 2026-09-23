package com.woodfurni.delivery.model;

import com.woodfurni.delivery.enums.DeliveryTripStatus;
import com.woodfurni.delivery.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Chuyến xe giao hàng — collection: "delivery_trips".
 *
 * Một chuyến xe gom nhiều đơn hàng (đang ở SHIPPING) để cùng giao trong 1 lượt.
 * Tổng tải trọng / thể tích / kích thước được tính lúc tạo và snapshot tại đây
 * (không tự cập nhật khi các đơn con thay đổi status về sau).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "delivery_trips")
public class DeliveryTrip {

    @Id
    private String id;

    /** Số chuyến — dạng TRIP-yyyyMMdd-xxxx. */
    @Indexed(unique = true)
    private String tripNumber;

    @Indexed
    private DeliveryTripStatus status;

    /** Mã loại xe đã chọn (xem {@link VehicleType}). */
    private String vehicleTypeCode;

    /** Tên hiển thị của loại xe (snapshot tại thời điểm tạo). */
    private String vehicleTypeName;

    /** User id của tài xế được gán. */
    @Indexed
    private String driverId;

    /** Tên tài xế (snapshot để hiển thị nhanh, không cần join users). */
    private String driverName;

    private String driverPhone;

    /** IDs của nhân viên lắp ráp (tối đa 2 người). */
    private List<String> assemblerIds;

    /** Tên nhân viên lắp ráp (snapshot). */
    private List<String> assemblerNames;

    /** Số đơn gán cho chuyến. */
    private int totalOrders;

    /** Tổng tải trọng (kg). */
    private double totalWeightKg;

    /** Tổng thể tích (m³). */
    private double totalVolumeM3;

    /** Kích thước kiện lớn nhất — length (cm). */
    private double maxLengthCm;

    /** Kích thước kiện lớn nhất — width (cm). */
    private double maxWidthCm;

    /** Kích thước kiện lớn nhất — height (cm). */
    private double maxHeightCm;

    /** Ghi chú của nhân viên tạo chuyến. */
    private String note;

    /** User id của người tạo chuyến. */
    private String createdBy;

    @Indexed
    private Instant createdAt;

    private Instant shippedAt;

    private Instant completedAt;
}
