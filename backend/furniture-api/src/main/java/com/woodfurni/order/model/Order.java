package com.woodfurni.order.model;

import com.woodfurni.order.enums.OrderStatus;
import com.woodfurni.order.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Order entity — collection: "orders"
 *
 * Checkout flow (OrderService.checkout):
 * 1. Read Cart → fail if empty
 * 2. Load shipping address → resolve city for shipping fee
 * 3. Reserve inventory for each item (rollback on failure)
 * 4. Validate promotion if provided
 * 5. Calculate shipping fee (from ShippingService) — snapshot at order time
 * 6. Generate orderNumber (ORD-yyyyMMdd-xxxx) and save Order
 * 7. Create Payment record (COD → PENDING; sandbox methods → SUCCESS)
 * 8. Increment promotion usage
 * 9. Clear cart
 *
 * As defined in WOODFURNI spec Mục 3.x / Order module.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
public class Order {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderNumber;

    @Indexed
    private String customerId;

    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    private ShippingAddress shippingAddress;

    private String promotionCode;

    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /**
     * Shipping fee at the moment of order placement.
     * Computed from ShippingService using the customer's city and cart weight.
     * Stored as a snapshot — never recalculated even if zone config changes.
     */
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    private BigDecimal subtotalAmount;

    private BigDecimal totalAmount;

    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Builder.Default
    private List<StatusHistoryEntry> statusHistory = new ArrayList<>();

    /**
     * Customer-facing shipment timeline.
     * Populated only while the order is SHIPPING — each {@code addTrackingUpdate}
     * call appends one entry. Survives the SHIPPING → DELIVERED transition so
     * the customer can review the full delivery history after delivery.
     */
    @Builder.Default
    private List<TrackingUpdate> trackingUpdates = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public void addStatusHistory(String status, String changedBy) {
        if (this.statusHistory == null) {
            this.statusHistory = new ArrayList<>();
        }
        this.statusHistory.add(StatusHistoryEntry.builder()
                .status(status)
                .changedAt(Instant.now())
                .changedBy(changedBy)
                .build());
    }

    /**
     * Append a tracking event to {@link #trackingUpdates}, initializing the
     * list if needed so callers don't have to null-check.
     */
    public void addTrackingUpdate(TrackingUpdate update) {
        if (this.trackingUpdates == null) {
            this.trackingUpdates = new ArrayList<>();
        }
        this.trackingUpdates.add(update);
    }

    public void calculateAmounts() {
        if (items != null && !items.isEmpty()) {
            this.subtotalAmount = items.stream()
                    .map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            this.subtotalAmount = BigDecimal.ZERO;
        }
        this.totalAmount = this.subtotalAmount
                .subtract(this.discountAmount != null ? this.discountAmount : BigDecimal.ZERO)
                .add(this.shippingFee != null ? this.shippingFee : BigDecimal.ZERO);
        if (this.totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            this.totalAmount = BigDecimal.ZERO;
        }
    }
}
