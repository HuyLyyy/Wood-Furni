package com.woodfurni.order.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tracking event for an order currently in SHIPPING state.
 *
 * Appended to {@code Order.trackingUpdates} every time warehouse/sales posts a
 * shipment status update. This is the timeline the customer sees on the
 * order detail page ("Đã lấy hàng" → "Đang vận chuyển" → "Đang giao").
 *
 * Final entry may also coincide with SHIPPING → DELIVERED transition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingUpdate {

    /** Short status label: "Đã lấy hàng", "Đang vận chuyển", "Đến kho phân loại", "Đang giao", ... */
    private String status;

    /** Current shipment location, e.g. "Kho HN", "Quận 1, HCM", "Đang giao đến 123 Nguyễn Huệ". */
    private String location;

    /** Optional free-text note (driver name, weather delay, partial delivery, ...). */
    private String note;

    /** When this update was recorded. */
    private Instant updatedAt;

    /** User ID (staff) who recorded this update. */
    private String updatedBy;
}