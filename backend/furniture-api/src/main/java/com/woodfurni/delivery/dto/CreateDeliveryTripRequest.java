package com.woodfurni.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request tạo chuyến xe — frontend gọi sau khi đã chọn đơn + chọn xe + chọn tài xế.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDeliveryTripRequest {
    @NotEmpty(message = "Phải chọn ít nhất 1 đơn hàng")
    private List<String> orderIds;

    @NotBlank(message = "Phải chọn loại xe")
    private String vehicleTypeCode;

    @NotBlank(message = "Phải chọn tài xế")
    private String driverId;

    /** Danh sách ID nhân viên lắp ráp (tối đa 2 người, optional). */
    private List<String> assemblerIds;

    /** Ghi chú (optional). */
    private String note;
}
