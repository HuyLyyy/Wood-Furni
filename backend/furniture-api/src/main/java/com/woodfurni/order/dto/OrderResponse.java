package com.woodfurni.order.dto;

import com.woodfurni.order.enums.OrderStatus;
import com.woodfurni.order.enums.PaymentMethod;
import com.woodfurni.order.enums.PaymentStatus;
import com.woodfurni.order.model.OrderItem;
import com.woodfurni.order.model.ShippingAddress;
import com.woodfurni.order.model.StatusHistoryEntry;
import com.woodfurni.order.model.TrackingUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private String id;
    private String orderNumber;
    private String customerId;
    private String customerCode;
    private List<OrderItem> items;
    private ShippingAddress shippingAddress;
    private String promotionCode;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal subtotalAmount;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private List<StatusHistoryEntry> statusHistory;
    private List<TrackingUpdate> trackingUpdates;
    private Instant createdAt;
    private Instant updatedAt;
}
