package com.woodfurni.delivery.service;

import com.woodfurni.auth.model.User;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.catalog.product.model.Product;
import com.woodfurni.catalog.product.repository.ProductRepository;
import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.delivery.dto.*;
import com.woodfurni.delivery.enums.DeliveryTripStatus;
import com.woodfurni.delivery.enums.VehicleType;
import com.woodfurni.delivery.model.DeliveryTrip;
import com.woodfurni.delivery.model.DeliveryTripOrder;
import com.woodfurni.delivery.repository.DeliveryTripOrderRepository;
import com.woodfurni.delivery.repository.DeliveryTripRepository;
import com.woodfurni.order.enums.OrderStatus;
import com.woodfurni.order.model.Order;
import com.woodfurni.order.model.OrderItem;
import com.woodfurni.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service chính cho module Chuyến xe.
 *
 * Phương thức:
 *   - listEligibleOrders(): các đơn ở SHIPPING chưa gán vào chuyến nào.
 *   - calculateOrderMetrics(Order): tính weight / volume / max dimensions
 *     từ Product.weight + Dimensions, kèm quantity.
 *   - previewCapacity(orderIds, vehicleType): xem tổng có lọt xe không.
 *   - createTrip(orderIds, vehicleType, driverId, ...): tạo chuyến.
 *   - listTrips(...) / getTripDetail(id).
 *
 * Quy ước đơn vị:
 *   weight: kg
 *   volume: m³ (cm³ / 1_000_000)
 *   dimensions: cm (length = chiều dài lớn nhất, width = chiều rộng, height = chiều cao)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final DeliveryTripRepository tripRepository;
    private final DeliveryTripOrderRepository tripOrderRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter TRIP_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ───────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Lấy các đơn SHIPPING — chưa được gán vào chuyến nào.
     * Mỗi đơn được bổ sung metrics (weight, volume, dim) từ Product.
     *
     * @param search từ khoá tìm theo orderNumber (substring, optional)
     * @param page   trang
     * @param size   kích thước trang
     */
    public Page<EligibleOrderResponse> listEligibleOrders(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders = (search != null && !search.isBlank())
                ? orderRepository.findByOrderNumberContaining(search, pageable)
                : orderRepository.findByStatus(OrderStatus.SHIPPING, pageable);

        // Filter: chỉ đơn chưa gán trip nào.
        List<Order> filtered = new ArrayList<>();
        for (Order o : orders.getContent()) {
            if (o.getStatus() != OrderStatus.SHIPPING) continue;
            if (!tripOrderRepository.existsByOrderId(o.getId())) {
                filtered.add(o);
            }
        }
        // Lưu ý: totalElements của page sẽ bị sai vì filter in-memory; cho UX đơn giản
        // ta trả totalElements = filtered.size() + (có thêm trang hay không).
        boolean hasMore = filtered.size() == size && orders.hasNext();
        List<EligibleOrderResponse> mapped = filtered.stream()
                .map(this::toEligibleOrder)
                .collect(Collectors.toList());
        return new org.springframework.data.domain.PageImpl<>(mapped, pageable, hasMore ? (long)(page + 2) * size : (long)filtered.size());
    }

    /**
     * Xem trước capacity: tổng của các đơn đã chọn có lọt vào loại xe đã chọn không.
     */
    public TripCapacityResponse previewCapacity(List<String> orderIds, String vehicleTypeCode) {
        VehicleType vehicle = VehicleType.fromCode(vehicleTypeCode);
        if (vehicle == null) {
            throw new IllegalArgumentException("Loại xe không hợp lệ: " + vehicleTypeCode);
        }
        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("Phải chọn ít nhất 1 đơn hàng.");
        }

        AggregatedMetrics agg = aggregate(orderIds);
        List<String> reasons = new ArrayList<>();

        // Validate từng tiêu chí.
        if (agg.totalWeightKg > vehicle.getMaxWeightKg()) {
            reasons.add(String.format(
                    "Tổng tải trọng %.1f kg vượt quá tải trọng tối đa của %s (%.1f kg).",
                    agg.totalWeightKg, vehicle.getDisplayName(), vehicle.getMaxWeightKg()));
        } else if (agg.totalWeightKg < vehicle.getMinWeightKg() && agg.totalOrders > 0) {
            // Cảnh báo dưới tải — không block, chỉ thông báo.
            reasons.add(String.format(
                    "⚠️ Tổng tải trọng %.1f kg thấp hơn tải tối thiểu của %s (%.1f kg) — xe chưa tận dụng hết.",
                    agg.totalWeightKg, vehicle.getDisplayName(), vehicle.getMinWeightKg()));
        }
        if (agg.totalVolumeM3 > vehicle.getMaxVolumeM3()) {
            reasons.add(String.format(
                    "Tổng thể tích %.2f m³ vượt quá thể tích chứa của %s (%.2f m³).",
                    agg.totalVolumeM3, vehicle.getDisplayName(), vehicle.getMaxVolumeM3()));
        }
        if (agg.maxLengthCm > vehicle.getMaxLengthCm()) {
            reasons.add(String.format(
                    "Đơn có kiện dài %.0f cm vượt quá chiều dài thùng %s (%.0f cm).",
                    agg.maxLengthCm, vehicle.getDisplayName(), vehicle.getMaxLengthCm()));
        }
        if (agg.maxWidthCm > vehicle.getMaxWidthCm()) {
            reasons.add(String.format(
                    "Đơn có kiện rộng %.0f cm vượt quá chiều rộng thùng %s (%.0f cm).",
                    agg.maxWidthCm, vehicle.getDisplayName(), vehicle.getMaxWidthCm()));
        }
        if (agg.maxHeightCm > vehicle.getMaxHeightCm()) {
            reasons.add(String.format(
                    "Đơn có kiện cao %.0f cm vượt quá chiều cao thùng %s (%.0f cm).",
                    agg.maxHeightCm, vehicle.getDisplayName(), vehicle.getMaxHeightCm()));
        }

        // fits = false khi có reason KHÔNG phải warning (⚠️).
        boolean blocking = reasons.stream().anyMatch(r -> !r.startsWith("⚠️"));
        boolean fits = !blocking;

        return TripCapacityResponse.builder()
                .totalOrders(agg.totalOrders)
                .totalWeightKg(agg.totalWeightKg)
                .totalVolumeM3(agg.totalVolumeM3)
                .maxLengthCm(agg.maxLengthCm)
                .maxWidthCm(agg.maxWidthCm)
                .maxHeightCm(agg.maxHeightCm)
                .vehicle(VehicleTypeResponse.from(vehicle))
                .reasons(reasons)
                .fits(fits)
                .build();
    }

    /**
     * Tạo chuyến xe. Validate trước — nếu không fits → throw exception.
     */
    public DeliveryTripResponse createTrip(List<String> orderIds,
                                           String vehicleTypeCode,
                                           String driverId,
                                           String note,
                                           String createdByUserId) {
        TripCapacityResponse preview = previewCapacity(orderIds, vehicleTypeCode);
        // Chặn tạo nếu có lý do blocking (không phải warning).
        boolean blocking = preview.getReasons().stream().anyMatch(r -> !r.startsWith("⚠️"));
        if (blocking || !preview.isFits()) {
            throw new IllegalArgumentException(
                    "Không thể tạo chuyến — " + String.join("; ", preview.getReasons()));
        }

        VehicleType vehicle = VehicleType.fromCode(vehicleTypeCode);
        AggregatedMetrics agg = aggregate(orderIds);

        // Resolve driver info (snapshot tại thời điểm tạo)
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));
        String driverName = driver.getFullName();
        String driverPhone = driver.getPhone();

        DeliveryTrip trip = DeliveryTrip.builder()
                .tripNumber(generateTripNumber())
                .status(DeliveryTripStatus.PLANNING)
                .vehicleTypeCode(vehicle.name())
                .vehicleTypeName(vehicle.getDisplayName())
                .driverId(driverId)
                .driverName(driverName)
                .driverPhone(driverPhone)
                .totalOrders(agg.totalOrders)
                .totalWeightKg(agg.totalWeightKg)
                .totalVolumeM3(agg.totalVolumeM3)
                .maxLengthCm(agg.maxLengthCm)
                .maxWidthCm(agg.maxWidthCm)
                .maxHeightCm(agg.maxHeightCm)
                .note(note)
                .createdBy(createdByUserId)
                .createdAt(Instant.now())
                .build();

        DeliveryTrip saved = tripRepository.save(trip);

        // Tạo join records
        AtomicInteger seq = new AtomicInteger(1);
        for (String orderId : orderIds) {
            Order o = orderRepository.findById(orderId)
                    .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
            OrderMetrics m = calculateOrderMetrics(o);

            DeliveryTripOrder link = DeliveryTripOrder.builder()
                    .tripId(saved.getId())
                    .orderId(o.getId())
                    .sequence(seq.getAndIncrement())
                    .weightKg(m.weightKg)
                    .volumeM3(m.volumeM3)
                    .lengthCm(m.lengthCm)
                    .widthCm(m.widthCm)
                    .heightCm(m.heightCm)
                    .addedAt(Instant.now())
                    .build();
            tripOrderRepository.save(link);
        }

        log.info("[DeliveryTrip] Created {} with {} orders, weight={}kg, volume={}m³",
                saved.getTripNumber(), saved.getTotalOrders(),
                saved.getTotalWeightKg(), saved.getTotalVolumeM3());

        return toTripResponse(saved, true);
    }

    /**
     * List trips với filter status + search theo tripNumber.
     */
    public Page<DeliveryTripResponse> listTrips(String statusCode, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DeliveryTrip> trips;

        if (search != null && !search.isBlank()) {
            trips = tripRepository.findByTripNumberContaining(search, pageable);
        } else if (statusCode != null && !statusCode.isBlank()) {
            DeliveryTripStatus st = parseStatus(statusCode);
            trips = (st == null) ? tripRepository.findAll(pageable) : tripRepository.findByStatus(st, pageable);
        } else {
            trips = tripRepository.findAll(pageable);
        }

        return trips.map(t -> toTripResponse(t, false));
    }

    public DeliveryTripResponse getTripDetail(String id) {
        DeliveryTrip t = tripRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("DeliveryTrip not found: " + id));
        return toTripResponse(t, true);
    }

    // ───────────────────────────────────────────────────────────────────────
    // HELPERS
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Tính metrics (weight, volume, dimensions) cho 1 order, dựa trên Product.
     * weight = sum(product.weight * quantity). volume = sum(w*d*h / 1e6 * quantity).
     * max dimensions = lấy max theo từng chiều (length, width, height) trên tất cả items.
     *
     * Product không tìm thấy hoặc thiếu dimensions/weight → 0 (best effort).
     */
    public OrderMetrics calculateOrderMetrics(Order order) {
        double totalWeight = 0;
        double totalVolume = 0;
        double maxL = 0, maxW = 0, maxH = 0;

        if (order.getItems() == null) return new OrderMetrics(0, 0, 0, 0, 0, 0);

        for (OrderItem item : order.getItems()) {
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            if (qty <= 0) continue;

            Product p = productRepository.findById(item.getProductId()).orElse(null);
            if (p == null) continue;

            // Weight (kg)
            if (p.getWeight() != null) {
                totalWeight += p.getWeight().doubleValue() * qty;
            }

            // Dimensions (cm)
            if (p.getDimensions() != null
                    && p.getDimensions().getDepth() != null
                    && p.getDimensions().getWidth() != null
                    && p.getDimensions().getHeight() != null) {
                double d = p.getDimensions().getDepth().doubleValue();
                double w = p.getDimensions().getWidth().doubleValue();
                double h = p.getDimensions().getHeight().doubleValue();

                // Volume mỗi kiện = (d × w × h) / 1_000_000  (cm³ → m³)
                totalVolume += (d * w * h / 1_000_000.0) * qty;

                // Lấy max từng chiều qua tất cả items (giả định 1 kiện = 1 product; qty thì cùng kích thước).
                maxL = Math.max(maxL, d);
                maxW = Math.max(maxW, w);
                maxH = Math.max(maxH, h);
            }
        }

        int itemCount = order.getItems() != null
                ? order.getItems().stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum()
                : 0;

        return new OrderMetrics(totalWeight, totalVolume, maxL, maxW, maxH, itemCount);
    }

    private AggregatedMetrics aggregate(List<String> orderIds) {
        double totalWeight = 0, totalVolume = 0;
        double maxL = 0, maxW = 0, maxH = 0;
        for (String oid : orderIds) {
            Order o = orderRepository.findById(oid)
                    .orElseThrow(() -> new EntityNotFoundException("Order not found: " + oid));
            OrderMetrics m = calculateOrderMetrics(o);
            totalWeight += m.weightKg;
            totalVolume += m.volumeM3;
            maxL = Math.max(maxL, m.lengthCm);
            maxW = Math.max(maxW, m.widthCm);
            maxH = Math.max(maxH, m.heightCm);
        }
        return new AggregatedMetrics(orderIds.size(), totalWeight, totalVolume, maxL, maxW, maxH);
    }

    private EligibleOrderResponse toEligibleOrder(Order o) {
        OrderMetrics m = calculateOrderMetrics(o);
        boolean already = tripOrderRepository.existsByOrderId(o.getId());
        return EligibleOrderResponse.builder()
                .orderId(o.getId())
                .orderNumber(o.getOrderNumber())
                .customerName(o.getShippingAddress() != null ? o.getShippingAddress().getLabel() : null)
                .customerPhone(o.getShippingAddress() != null ? o.getShippingAddress().getPhone() : null)
                .shippingCity(o.getShippingAddress() != null ? o.getShippingAddress().getCity() : null)
                .shippingDistrict(o.getShippingAddress() != null ? o.getShippingAddress().getDistrict() : null)
                .shippingAddressLine(o.getShippingAddress() != null ? o.getShippingAddress().getLine1() : null)
                .createdAt(o.getCreatedAt())
                .itemCount(m.itemCount)
                .weightKg(round1(m.weightKg))
                .volumeM3(round2(m.volumeM3))
                .lengthCm(round1(m.lengthCm))
                .widthCm(round1(m.widthCm))
                .heightCm(round1(m.heightCm))
                .alreadyAssigned(already)
                .build();
    }

    private DeliveryTripResponse toTripResponse(DeliveryTrip t, boolean withOrders) {
        VehicleType vehicle = VehicleType.fromCode(t.getVehicleTypeCode());
        var b = DeliveryTripResponse.<DeliveryTripResponse>builder()
                .id(t.getId())
                .tripNumber(t.getTripNumber())
                .status(t.getStatus())
                .vehicle(vehicle == null ? null : VehicleTypeResponse.from(vehicle))
                .driverId(t.getDriverId())
                .driverName(t.getDriverName())
                .driverPhone(t.getDriverPhone())
                .totalOrders(t.getTotalOrders())
                .totalWeightKg(round1(t.getTotalWeightKg()))
                .totalVolumeM3(round2(t.getTotalVolumeM3()))
                .maxLengthCm(round1(t.getMaxLengthCm()))
                .maxWidthCm(round1(t.getMaxWidthCm()))
                .maxHeightCm(round1(t.getMaxHeightCm()))
                .note(t.getNote())
                .createdBy(t.getCreatedBy())
                .createdAt(t.getCreatedAt())
                .shippedAt(t.getShippedAt())
                .completedAt(t.getCompletedAt());

        if (withOrders) {
            List<DeliveryTripOrder> links = tripOrderRepository.findByTripIdOrderBySequenceAsc(t.getId());
            List<TripOrderResponse> orders = new ArrayList<>();
            for (DeliveryTripOrder link : links) {
                Order o = orderRepository.findById(link.getOrderId()).orElse(null);
                if (o == null) continue;
                orders.add(TripOrderResponse.builder()
                        .orderId(o.getId())
                        .orderNumber(o.getOrderNumber())
                        .customerName(o.getShippingAddress() != null ? o.getShippingAddress().getLabel() : null)
                        .customerPhone(o.getShippingAddress() != null ? o.getShippingAddress().getPhone() : null)
                        .shippingCity(o.getShippingAddress() != null ? o.getShippingAddress().getCity() : null)
                        .shippingDistrict(o.getShippingAddress() != null ? o.getShippingAddress().getDistrict() : null)
                        .shippingAddressLine(o.getShippingAddress() != null ? o.getShippingAddress().getLine1() : null)
                        .sequence(link.getSequence())
                        .itemCount(link.getWeightKg() > 0 ? 1 : 0) // best-effort placeholder
                        .weightKg(round1(link.getWeightKg()))
                        .volumeM3(round2(link.getVolumeM3()))
                        .lengthCm(round1(link.getLengthCm()))
                        .widthCm(round1(link.getWidthCm()))
                        .heightCm(round1(link.getHeightCm()))
                        .orderStatus(o.getStatus().name())
                        .orderCreatedAt(o.getCreatedAt())
                        .build());
            }
            b.orders(orders);
        }
        return b.build();
    }

    private String generateTripNumber() {
        String date = LocalDate.now().format(TRIP_DATE_FMT);
        // Đếm nhanh số trip đã tạo trong ngày để sinh hậu tố 4 số.
        long count;
        try {
            count = tripRepository.count() + 1;
        } catch (Exception e) {
            count = System.currentTimeMillis() % 10000;
        }
        return String.format("TRIP-%s-%04d", date, count % 10000);
    }

    private DeliveryTripStatus parseStatus(String code) {
        try { return DeliveryTripStatus.valueOf(code.toUpperCase()); }
        catch (Exception e) { return null; }
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    // ─── Value types ────────────────────────────────────────────────────────

    public record OrderMetrics(double weightKg, double volumeM3,
                               double lengthCm, double widthCm, double heightCm,
                               int itemCount) {}

    public record AggregatedMetrics(int totalOrders,
                                    double totalWeightKg, double totalVolumeM3,
                                    double maxLengthCm, double maxWidthCm, double maxHeightCm) {}
}
