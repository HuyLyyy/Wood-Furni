package com.woodfurni.cart.dto;

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
public class CartResponse {

    private String id;
    private String userId;
    private List<CartItemResponse> items;
    private BigDecimal totalAmount;
    private Integer itemCount;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemResponse {
        private String productId;
        private String productName;
        private String productSlug;
        private String productImage;
        private BigDecimal unitPrice;
        private Integer quantity;
        private BigDecimal subtotal;
    }
}
