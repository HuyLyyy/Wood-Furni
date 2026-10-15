package com.woodfurni.delivery.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO xác nhận giao thành công một đơn trong chuyến.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkOrderDeliveredRequest {

    /** Tên người nhận thực tế (nếu khác tên chính). Optional. */
    private String receiverName;

    /** Ghi chú của tài xế (VD: "Khách ký nhận tại cổng"). Optional. */
    private String note;

    /** Ảnh chứng từ đã giao. Optional. */
    private String proofPhotoUrl;

    /** Cho biết có thu COD hay không. */
    @NotNull
    private Boolean codCollected;
}
