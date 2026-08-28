package com.woodfurni.cart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Shopping cart entity.
 * Collection: "carts"
 *
 * Rules:
 * - 1 active cart per user (enforced by unique userId index)
 * - Cart does NOT reserve inventory — only validates availability at add-time
 * - Price snapshots are refreshed on every GET (not stored long-term)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "carts")
public class Cart {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @LastModifiedDate
    private Instant updatedAt;

    public void calculateTotalAmount() {
        this.totalAmount = items.stream()
                .map(item -> {
                    item.calculateSubtotal();
                    return item.getSubtotal();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
