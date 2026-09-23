package com.woodfurni.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request để frontend preview xem các đơn đã chọn có lọt xe không.
 * BE validate capacity, trả về {@link TripCapacityResponse}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCapacityRequest {
    /** Danh sách orderId đã chọn. */
    private List<String> orderIds;
    /** Code loại xe (VAN_SMALL / TRUCK_LIGHT / TRUCK_MEDIUM). */
    private String vehicleTypeCode;
}
