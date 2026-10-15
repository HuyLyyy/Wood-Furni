package com.woodfurni.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO khi tài xế gọi mà khách không nghe máy. Optional — chỉ cần note / photo
 * nếu có.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkOrderUnreachableNote {
    private String note;
    private String proofPhotoUrl;
}
