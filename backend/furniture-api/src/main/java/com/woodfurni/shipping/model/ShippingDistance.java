package com.woodfurni.shipping.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * ShippingDistance — estimated delivery distance from the warehouse
 * (Quận 8, TP.HCM) to each city/district zone.
 *
 * Collection: "shippingdistances"
 * Index: { city: 1, district: 1 } unique
 *
 * For cities other than "Hồ Chí Minh", the distance table is not consulted;
 * {@link com.woodfurni.shipping.service.ShippingService} applies the
 * {@code out-of-province-distance-km} fallback configured in application.yml.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "shippingdistances")
@CompoundIndex(name = "idx_city_district", def = "{'city': 1, 'district': 1}", unique = true)
public class ShippingDistance {

    @Id
    private String id;

    /**
     * City name. Must exactly match the value passed in checkout requests.
     * Example: "Hồ Chí Minh"
     */
    @Field("city")
    private String city;

    /**
     * District name. Must exactly match the value passed in checkout requests.
     * Example: "Quận 1"
     */
    @Field("district")
    private String district;

    /**
     * Estimated road distance in kilometres from the warehouse (Quận 8) to
     * the centre of this district.
     *
     * This is a reference value — adjust it to reflect actual delivery routes
     * in your area.
     */
    @Field("distanceKm")
    private BigDecimal distanceKm;
}
