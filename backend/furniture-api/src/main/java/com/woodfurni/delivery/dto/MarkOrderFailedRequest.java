package com.woodfurni.delivery.dto;

import com.woodfurni.delivery.enums.RescheduleReason;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO khi tài xế gặp tình huống không liên lạc được khách / khách từ chối
 * dứt khoát → đánh dấu giao thất bại.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkOrderFailedRequest {

    @NotNull
    private RescheduleReason reason;

    private String customReason;

    private String note;

    private String proofPhotoUrl;
}
