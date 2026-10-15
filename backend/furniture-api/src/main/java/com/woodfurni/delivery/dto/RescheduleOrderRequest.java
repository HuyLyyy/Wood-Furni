package com.woodfurni.delivery.dto;

import com.woodfurni.delivery.enums.RescheduleReason;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO yêu cầu hoãn giao hàng cho MỘT đơn trong chuyến.
 *
 * Theo luồng nghiệp vụ: tài xế đến nơi, gọi khách không được / khách từ
 * chối → mở form "Hoãn giao hàng" → điền thông tin này.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleOrderRequest {

    @NotNull
    private RescheduleReason reason;

    /** Bắt buộc khi reason = OTHER. */
    private String customReason;

    @NotNull
    private Instant nextDeliveryDate;

    /** Khung giờ mong muốn (VD: "09:00-12:00"). Optional. */
    private String preferredTimeWindow;

    /** Ghi chú thêm (VD: "Khách đi công tác đột xuất"). */
    private String note;

    /** Ảnh xác nhận từ tài xế (URL). Optional. */
    private String proofPhotoUrl;
}
