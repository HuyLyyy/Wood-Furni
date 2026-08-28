package com.woodfurni.shipping.dto;

import com.woodfurni.shipping.model.ShippingDistance;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for POST /shipping/distances and PUT /shipping/distances/{id}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingDistanceRequest {

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "District is required")
    private String district;

    @Min(value = 0, message = "Distance cannot be negative")
    private BigDecimal distanceKm;

    public static ShippingDistance toEntity(ShippingDistanceRequest r) {
        return ShippingDistance.builder()
                .city(r.getCity())
                .district(r.getDistrict())
                .distanceKm(r.getDistanceKm())
                .build();
    }
}
