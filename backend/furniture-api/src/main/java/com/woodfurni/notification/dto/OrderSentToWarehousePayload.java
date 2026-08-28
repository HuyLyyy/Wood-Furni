package com.woodfurni.notification.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderSentToWarehousePayload {
    private String orderId;
    private String orderNumber;
    private int itemCount;
}
