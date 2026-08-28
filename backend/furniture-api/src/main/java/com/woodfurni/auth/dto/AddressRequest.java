package com.woodfurni.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Address request payload for creating/updating user addresses.
 * Embedded inside User document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest {

    private String id;

    private String label;

    @NotBlank(message = "Address line is required")
    private String line1;

    @NotBlank(message = "Ward is required")
    private String ward;

    @NotBlank(message = "District is required")
    private String district;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "Phone is required")
    private String phone;

    private Boolean isDefault;
}
