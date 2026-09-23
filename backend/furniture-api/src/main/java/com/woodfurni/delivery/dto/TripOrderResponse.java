package com.woodfurni.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Một dòng trong bảng đơn của chuyến xe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripOrderResponse {
    private String orderId;
    private String orderNumber;
    private String customerName;
    private String customerPhone;
    private String shippingCity;
    private String shippingDistrict;
    private String shippingAddressLine;
    private int sequence;
    private int itemCount;
    private double weightKg;
    private double volumeM3;
    private double lengthCm;
    private double widthCm;
    private double heightCm;
    private String orderStatus;
    private Instant orderCreatedAt;
}
