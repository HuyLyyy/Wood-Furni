package com.woodfurni.shipping.dto;

import com.woodfurni.shipping.model.ShippingDistance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Public DTO for ShippingDistance — returned by GET /shipping/distances.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingDistanceResponse {

    private String id;
    private String city;
    private String district;
    private BigDecimal distanceKm;

    public static ShippingDistanceResponse fromEntity(ShippingDistance e) {
        return ShippingDistanceResponse.builder()
                .id(e.getId())
                .city(e.getCity())
                .district(e.getDistrict())
                .distanceKm(e.getDistanceKm())
                .build();
    }
}
