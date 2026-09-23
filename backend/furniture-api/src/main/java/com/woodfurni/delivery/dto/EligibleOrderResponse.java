package com.woodfurni.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Một đơn hàng (ở trạng thái SHIPPING) mà staff có thể gán vào chuyến xe.
 *
 * Trường weightKg / volumeM3 / lengthCm / widthCm / heightCm là snapshot
 * tính từ Product (kèm quantity) tại thời điểm trả về. Frontend dùng để
 * hiển thị và tính tổng ngay khi user tick chọn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibleOrderResponse {
    private String orderId;
    private String orderNumber;
    private String customerName;
    private String customerPhone;
    private String shippingCity;
    private String shippingDistrict;
    private String shippingAddressLine;
    private Instant createdAt;
    private int itemCount;
    private double weightKg;
    private double volumeM3;
    private double lengthCm;
    private double widthCm;
    private double heightCm;
    /** Cờ phụ: order này đã bị gán vào 1 chuyến khác chưa? (true = đã gán → không hiển thị) */
    private boolean alreadyAssigned;
    /** Nếu alreadyAssigned = true thì trả thêm tripNumber để debug. */
    private String assignedTripNumber;
}
