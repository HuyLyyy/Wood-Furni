package com.woodfurni.delivery.service;

import com.woodfurni.auth.model.User;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.catalog.product.model.Product;
import com.woodfurni.catalog.product.repository.ProductRepository;
import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.delivery.dto.*;
import com.woodfurni.delivery.enums.DeliveryTripStatus;
import com.woodfurni.delivery.enums.OrderDeliveryState;
import com.woodfurni.delivery.enums.RescheduleReason;
import com.woodfurni.delivery.enums.VehicleType;
import com.woodfurni.delivery.model.DeliveryTrip;
import com.woodfurni.delivery.model.DeliveryTripOrder;
import com.woodfurni.delivery.repository.DeliveryTripOrderRepository;
import com.woodfurni.delivery.repository.DeliveryTripRepository;
import com.woodfurni.notification.client.NotificationClient;
import com.woodfurni.order.enums.OrderStatus;
import com.woodfurni.order.model.Order;
import com.woodfurni.order.model.OrderItem;
import com.woodfurni.order.model.TrackingUpdate;
import com.woodfurni.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    private static final int MAX_ASSEMBLERS_PER_TRIP = 2;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final DeliveryTripRepository tripRepository;
    private final DeliveryTripOrderRepository tripOrderRepository;
    private final UserRepository userRepository;
    private final NotificationClient notificationClient;

    private static final DateTimeFormatter TRIP_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ───────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Lấy các đơn hàng có thể gán vào chuyến xe mới.
     * Bao gồm:
     *  - Đơn SHIPPING chưa gán trip nào (đang chờ giao)
     *  - Đơn PROCESSING chưa gán trip nào (đang chờ tạo trip mới sau khi trip bị hủy)
     *
     * @param search từ khoá tìm theo orderNumber (substring, optional)
     * @param page   trang
     * @param size   kích thước trang
     */
    public Page<EligibleOrderResponse> listEligibleOrders(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Load all PROCESSING and SHIPPING orders
        List<Order> allOrders = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            Page<Order> bySearch = orderRepository.findByOrderNumberContaining(search, pageable);
            allOrders.addAll(bySearch.getContent());
        } else {
            allOrders.addAll(orderRepository.findByStatus(OrderStatus.PROCESSING, pageable).getContent());
            allOrders.addAll(orderRepository.findByStatus(OrderStatus.SHIPPING, pageable).getContent());
        }

        // Filter: chỉ đơn chưa gán trip nào và có status phù hợp (PROCESSING hoặc SHIPPING)
        List<Order> filtered = new ArrayList<>();
        for (Order o : allOrders) {
            if (o.getStatus() != OrderStatus.PROCESSING && o.getStatus() != OrderStatus.SHIPPING) continue;
            if (!tripOrderRepository.existsByOrderId(o.getId())) {
                filtered.add(o);
            }
        }

        // Sort by createdAt desc and deduplicate
        filtered.sort((a, b) -> {
            if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        // Paginate filtered results
        int fromIndex = 0;
        int toIndex = Math.min(size, filtered.size());
        List<Order> pageContent = filtered.subList(fromIndex, toIndex);

        List<EligibleOrderResponse> mapped = pageContent.stream()
                .map(this::toEligibleOrder)
                .collect(Collectors.toList());

        boolean hasMore = filtered.size() > size;
        return new org.springframework.data.domain.PageImpl<>(mapped, pageable, hasMore ? (long)filtered.size() : (long)pageContent.size());
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
                                           List<String> assemblerIds,
                                           String note,
                                           String createdByUserId) {
        // Validate assembler count
        if (assemblerIds != null && assemblerIds.size() > MAX_ASSEMBLERS_PER_TRIP) {
            throw new IllegalArgumentException(
                    "Số lượng nhân viên lắp ráp không được vượt quá " + MAX_ASSEMBLERS_PER_TRIP);
        }

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

        // Resolve assembler names if provided
        List<String> assemblerNames = new ArrayList<>();
        if (assemblerIds != null && !assemblerIds.isEmpty()) {
            for (String assemblerId : assemblerIds) {
                User assembler = userRepository.findById(assemblerId)
                        .orElseThrow(() -> new EntityNotFoundException("Assembler not found: " + assemblerId));
                assemblerNames.add(assembler.getFullName());
            }
        }

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
                .assemblerIds(assemblerIds)
                .assemblerNames(assemblerNames)
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

        log.info("[DeliveryTrip] Created {} with {} orders, weight={}kg, volume={}m3",
                saved.getTripNumber(), saved.getTotalOrders(),
                saved.getTotalWeightKg(), saved.getTotalVolumeM3());

        return toTripResponse(saved, true);
    }

    /**
     * List trips với filter status + search theo tripNumber.
     *
     * Search hỗ trợ NHIỀU mã chuyến cùng lúc, phân cách bởi dấu phẩy hoặc khoảng trắng.
     * Ví dụ: "TRIP-20260923-0001, TRIP-20260922-0002 TRIP-20260921-0003"
     * → match exact (case-insensitive) với từng mã.
     * Nếu chỉ có 1 mã → fallback tìm LIKE (contains) để hỗ trợ tìm gần đúng.
     */
    public Page<DeliveryTripResponse> listTrips(String statusCode, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DeliveryTrip> trips;

        // Parse search: tách theo ',' hoặc khoảng trắng, trim, bỏ rỗng
        List<String> codes = parseTripSearchTokens(search);

        if (!codes.isEmpty()) {
            // Nếu nhiều hơn 1 mã → tìm exact match (in) cho từng mã
            if (codes.size() > 1) {
                List<DeliveryTrip> matched = tripRepository.findByTripNumberInIgnoreCase(codes);
                // Lọc thêm theo status nếu có
                if (statusCode != null && !statusCode.isBlank()) {
                    DeliveryTripStatus st = parseStatus(statusCode);
                    if (st != null) {
                        matched = matched.stream()
                                .filter(t -> t.getStatus() == st)
                                .collect(Collectors.toList());
                    }
                }
                // Sắp xếp createdAt desc để nhất quán với các nhánh khác
                matched = matched.stream()
                        .sorted(Comparator.comparing(DeliveryTrip::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .collect(Collectors.toList());
                trips = new PageImpl<>(matched, pageable, matched.size());
            } else {
                // 1 mã duy nhất → LIKE để tiện tìm gần đúng
                String single = codes.get(0);
                Page<DeliveryTrip> byLike = tripRepository.findByTripNumberContaining(single, pageable);
                if (statusCode != null && !statusCode.isBlank()) {
                    DeliveryTripStatus st = parseStatus(statusCode);
                    if (st != null) {
                        List<DeliveryTrip> filtered = byLike.getContent().stream()
                                .filter(t -> t.getStatus() == st)
                                .collect(Collectors.toList());
                        trips = new PageImpl<>(filtered, pageable, filtered.size());
                    } else {
                        trips = byLike;
                    }
                } else {
                    trips = byLike;
                }
            }
        } else if (statusCode != null && !statusCode.isBlank()) {
            DeliveryTripStatus st = parseStatus(statusCode);
            trips = (st == null) ? tripRepository.findAll(pageable) : tripRepository.findByStatus(st, pageable);
        } else {
            trips = tripRepository.findAll(pageable);
        }

        return trips.map(t -> toTripResponse(t, false));
    }

    /**
     * Tách chuỗi search thành danh sách mã chuyến:
     *   "TRIP-1, TRIP-2 TRIP-3 , TRIP-4"
     * → ["TRIP-1","TRIP-2","TRIP-3","TRIP-4"]
     */
    private List<String> parseTripSearchTokens(String search) {
        if (search == null || search.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(search.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    public DeliveryTripResponse getTripDetail(String id) {
        DeliveryTrip t = tripRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("DeliveryTrip not found: " + id));
        return toTripResponse(t, true);
    }

    // ───────────────────────────────────────────────────────────────────────
    // TRIP ACTIONS
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Bắt đầu giao hàng — chuyến chuyển sang SHIPPING.
     * Chỉ chuyến ở trạng thái PLANNING mới được bắt đầu giao.
     */
    public DeliveryTripResponse startShipping(String tripId, String performedBy) {
        DeliveryTrip t = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("DeliveryTrip not found: " + tripId));

        if (t.getStatus() != DeliveryTripStatus.PLANNING) {
            throw new IllegalStateException(
                    "Chỉ chuyến ở trạng thái LÊN KẾ HOẠCH mới có thể bắt đầu giao. Trạng thái hiện tại: " + t.getStatus());
        }

        t.setStatus(DeliveryTripStatus.SHIPPING);
        t.setShippedAt(Instant.now());

        DeliveryTrip saved = tripRepository.save(t);
        log.info("[DeliveryTrip] {} started shipping by {}", saved.getTripNumber(), performedBy);

        return toTripResponse(saved, true);
    }

    /**
     * Hủy chuyến xe — chuyến chuyển sang CANCELLED.
     * Chỉ chuyến ở trạng thái PLANNING mới được hủy.
     *
     * Khi hủy:
     * 1. Các đơn hàng trong chuyến được chuyển về PROCESSING (chưa giao)
     *    để Warehouse có thể tạo chuyến xe mới.
     * 2. Xóa các bản ghi join giữa trip và orders.
     */
    public DeliveryTripResponse cancelTrip(String tripId, String reason, String performedBy) {
        DeliveryTrip t = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("DeliveryTrip not found: " + tripId));

        if (t.getStatus() != DeliveryTripStatus.PLANNING) {
            throw new IllegalStateException(
                    "Chỉ chuyến ở trạng thái LÊN KẾ HOẠCH mới có thể hủy. Trạng thái hiện tại: " + t.getStatus());
        }

        // ── 1. Restore order statuses back to PROCESSING ──────────────────
        List<DeliveryTripOrder> tripOrders = tripOrderRepository.findByTripIdOrderBySequenceAsc(tripId);
        List<String> restoredOrderNumbers = new ArrayList<>();
        for (DeliveryTripOrder link : tripOrders) {
            try {
                Order order = orderRepository.findById(link.getOrderId()).orElse(null);
                if (order != null && order.getStatus() == OrderStatus.SHIPPING) {
                    order.setStatus(OrderStatus.PROCESSING);
                    order.addStatusHistory(OrderStatus.PROCESSING.name(), performedBy,
                            "Chuyến xe " + t.getTripNumber() + " bị hủy — chờ tạo chuyến mới");
                    orderRepository.save(order);
                    restoredOrderNumbers.add(order.getOrderNumber());
                }
            } catch (Exception e) {
                log.warn("[DeliveryTrip] Failed to restore order {}: {}", link.getOrderId(), e.getMessage());
            }
        }

        // ── 2. Delete trip-order join records ─────────────────────────────
        tripOrderRepository.deleteByTripId(tripId);

        // ── 3. Cancel the trip ────────────────────────────────────────────
        t.setStatus(DeliveryTripStatus.CANCELLED);
        t.setCancelledAt(Instant.now());
        t.setCancelledBy(performedBy);
        t.setCancelReason(reason);

        DeliveryTrip saved = tripRepository.save(t);
        log.info("[DeliveryTrip] {} cancelled by {} - restored {} orders to PROCESSING: {}",
                saved.getTripNumber(), performedBy, restoredOrderNumbers.size(), restoredOrderNumbers);

        return toTripResponse(saved, true);
    }

    /**
     * Hoàn thành chuyến xe — chuyến chuyển sang COMPLETED.
     * Chỉ chuyến ở trạng thái SHIPPING mới được hoàn thành.
     * Thường do Sales/Admin xác nhận sau khi nhận lại hàng từ tài xế.
     */
    public DeliveryTripResponse completeTrip(String tripId, String performedBy) {
        DeliveryTrip t = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("DeliveryTrip not found: " + tripId));

        if (t.getStatus() != DeliveryTripStatus.SHIPPING) {
            throw new IllegalStateException(
                    "Chỉ chuyến đang GIAO mới có thể hoàn thành. Trạng thái hiện tại: " + t.getStatus());
        }

        t.setStatus(DeliveryTripStatus.COMPLETED);
        t.setCompletedAt(Instant.now());

        DeliveryTrip saved = tripRepository.save(t);
        log.info("[DeliveryTrip] {} completed by {}", saved.getTripNumber(), performedBy);

        return toTripResponse(saved, true);
    }

    // ───────────────────────────────────────────────────────────────────────
    // LUỒNG HOÃN GIAO HÀNG (Bước 1→9 trong business flow)
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Bước 1: Tài xế đến địa điểm giao hàng.
     * SHIPPING → ARRIVED.
     */
    public DeliveryTripResponse arriveAtLocation(String tripId, String performedBy) {
        DeliveryTrip t = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("DeliveryTrip not found: " + tripId));

        if (t.getStatus() != DeliveryTripStatus.SHIPPING) {
            throw new IllegalStateException(
                    "Chỉ chuyến đang GIAO mới có thể đánh dấu đã đến nơi. Trạng thái hiện tại: " + t.getStatus());
        }

        t.setStatus(DeliveryTripStatus.ARRIVED);
        t.setArrivedAt(Instant.now());

        DeliveryTrip saved = tripRepository.save(t);
        log.info("[DeliveryTrip] {} arrived by {}", saved.getTripNumber(), performedBy);

        return toTripResponse(saved, true);
    }

    /**
     * Bước 2A: Tài xế xác nhận giao thành công một đơn.
     * - link.deliveryState = DELIVERED
     * - order.status: SHIPPING → DELIVERED, payment_status: nếu COD thì UNPAID→PAID khi codCollected=true
     * - Nếu TẤT CẢ đơn trong chuyến đã xử lý xong → trip COMPLETED.
     */
    public DeliveryActionResult markOrderDelivered(String tripId, String orderId,
                                                   MarkOrderDeliveredRequest req, String performedBy) {
        DeliveryTrip t = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("DeliveryTrip not found: " + tripId));
        if (t.getStatus() != DeliveryTripStatus.SHIPPING && t.getStatus() != DeliveryTripStatus.ARRIVED) {
            throw new IllegalStateException(
                    "Chỉ chuyến đang GIAO hoặc ĐÃ ĐẾN mới có thể xác nhận giao. Trạng thái hiện tại: " + t.getStatus());
        }

        DeliveryTripOrder link = tripOrderRepository.findByTripIdAndOrderId(tripId, orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order " + orderId + " không thuộc chuyến " + tripId));

        if (link.getDeliveryState() != OrderDeliveryState.PENDING
                && link.getDeliveryState() != OrderDeliveryState.UNREACHABLE) {
            throw new IllegalStateException(
                    "Đơn này đã được xử lý (state=" + link.getDeliveryState() + ").");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        // Cập nhật link
        link.setDeliveryState(OrderDeliveryState.DELIVERED);
        link.setDeliveredAt(Instant.now());
        link.setDeliveryNote(req.getNote());
        link.setProofPhotoUrl(req.getProofPhotoUrl());
        tripOrderRepository.save(link);

        // Cập nhật order
        if (order.getStatus() != OrderStatus.SHIPPING) {
            // Trường hợp đơn từng bị hoãn (RESCHEDULED) rồi tạo chuyến mới → vẫn hợp lệ
            // cho phép chuyển sang DELIVERED.
            log.warn("[DeliveryTrip] Order {} was in {} before marking delivered — overriding.",
                    orderId, order.getStatus());
        }
        order.setStatus(OrderStatus.DELIVERED);
        order.addStatusHistory(OrderStatus.DELIVERED.name(), performedBy,
                req.getNote() != null ? req.getNote() : "Tài xế xác nhận đã giao.");
        order.addTrackingUpdate(TrackingUpdate.builder()
                .status("Đã giao")
                .location("Địa điểm khách hàng")
                .note(req.getReceiverName() != null
                        ? "Người nhận: " + req.getReceiverName()
                        : "Khách đã nhận hàng")
                .updatedAt(Instant.now())
                .updatedBy(performedBy)
                .build());
        orderRepository.save(order);

        // Tổng hợp chuyến
        return finalizeTripAfterAction(t, performedBy, "Đã giao đơn " + order.getOrderNumber());
    }

    /**
     * Bước 2C: Tài xế không liên lạc được khách / đã chờ 15-30 phút.
     * - link.deliveryState = UNREACHABLE
     * - order vẫn SHIPPING (chờ xác nhận cuối)
     * KHÔNG tự động FAILED — tài xế phải gọi lại hoặc escalate.
     */
    public DeliveryActionResult markOrderUnreachable(String tripId, String orderId,
                                                    String note, String proofPhotoUrl, String performedBy) {
        DeliveryTrip t = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("DeliveryTrip not found: " + tripId));
        if (t.getStatus() != DeliveryTripStatus.SHIPPING && t.getStatus() != DeliveryTripStatus.ARRIVED) {
            throw new IllegalStateException(
                    "Chỉ chuyến đang GIAO hoặc ĐÃ ĐẾN mới có thể đánh dấu không liên lạc được.");
        }

        DeliveryTripOrder link = tripOrderRepository.findByTripIdAndOrderId(tripId, orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order " + orderId + " không thuộc chuyến " + tripId));
        if (link.getDeliveryState() != OrderDeliveryState.PENDING) {
            throw new IllegalStateException(
                    "Đơn này đã được xử lý (state=" + link.getDeliveryState() + ").");
        }

        link.setDeliveryState(OrderDeliveryState.UNREACHABLE);
        link.setDeliveryNote(note);
        link.setProofPhotoUrl(proofPhotoUrl);
        link.setDeliveredAt(Instant.now());
        tripOrderRepository.save(link);

        return finalizeTripAfterAction(t, performedBy, "Đơn không liên lạc được");
    }

    /**
     * Bước 2B → 9: Tài xế xác nhận HOÃN giao hàng.
     *  - link.deliveryState = RESCHEDULED, rescheduleCount++, nextDeliveryDate
     *  - order.status: SHIPPING → RESCHEDULED, rescheduleCount++
     *  - trip.rescheduleCount++, lastRescheduleReason, nextDeliveryDate snapshot
     *  - Nếu lần thứ 3 → warning=true (CSKH can thiệp)
     *  - Nếu TẤT CẢ đơn trong chuyến đều RESCHEDULED/FAILED → trip = RESCHEDULED
     */
    public DeliveryActionResult rescheduleOrder(String tripId, String orderId,
                                                RescheduleOrderRequest req, String performedBy) {
        if (req.getReason() == RescheduleReason.OTHER
                && (req.getCustomReason() == null || req.getCustomReason().isBlank())) {
            throw new IllegalArgumentException("Khi chọn lý do KHÁC, vui lòng nhập mô tả cụ thể.");
        }
        if (req.getNextDeliveryDate() == null) {
            throw new IllegalArgumentException("Vui lòng chọn thời gian giao lại.");
        }

        DeliveryTrip t = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("DeliveryTrip not found: " + tripId));
        if (t.getStatus() != DeliveryTripStatus.SHIPPING
                && t.getStatus() != DeliveryTripStatus.ARRIVED
                && t.getStatus() != DeliveryTripStatus.RESCHEDULED) {
            throw new IllegalStateException(
                    "Chuyến ở trạng thái " + t.getStatus() + " — không thể hoãn giao.");
        }

        DeliveryTripOrder link = tripOrderRepository.findByTripIdAndOrderId(tripId, orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order " + orderId + " không thuộc chuyến " + tripId));
        if (link.getDeliveryState() == OrderDeliveryState.DELIVERED) {
            throw new IllegalStateException("Đơn đã giao thành công, không thể hoãn.");
        }
        if (link.getDeliveryState() == OrderDeliveryState.FAILED) {
            throw new IllegalStateException("Đơn đã giao thất bại, không thể hoãn — tạo đơn mới.");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        String finalReason = req.getReason() == RescheduleReason.OTHER
                ? req.getCustomReason()
                : req.getReason().name();

        // Cập nhật link
        link.setDeliveryState(OrderDeliveryState.RESCHEDULED);
        link.setRescheduleReason(req.getReason());
        link.setDeliveryNote(req.getNote());
        link.setProofPhotoUrl(req.getProofPhotoUrl());
        link.setRescheduleCount(link.getRescheduleCount() + 1);
        link.setNextDeliveryDate(req.getNextDeliveryDate());
        link.setDeliveredAt(Instant.now());
        tripOrderRepository.save(link);

        // Cập nhật order
        int newOrderRescheduleCount = order.getRescheduleCount() + 1;
        order.setRescheduleCount(newOrderRescheduleCount);
        order.setNextDeliveryDate(req.getNextDeliveryDate());
        order.setStatus(OrderStatus.RESCHEDULED);
        String warnNote = newOrderRescheduleCount >= 3
                ? " ⚠ CẢNH BÁO: Khách đã hoãn " + newOrderRescheduleCount + " lần — CSKH liên hệ."
                : null;
        order.addStatusHistory(OrderStatus.RESCHEDULED.name(), performedBy,
                "Hoãn giao: " + finalReason + (req.getNote() != null ? ". " + req.getNote() : "")
                        + (warnNote != null ? warnNote : ""));
        order.addTrackingUpdate(TrackingUpdate.builder()
                .status("Hoãn giao")
                .location("Địa điểm khách hàng")
                .note("Lý do: " + finalReason
                        + " | Giao lại: " + req.getNextDeliveryDate()
                        + (req.getPreferredTimeWindow() != null
                                ? " (" + req.getPreferredTimeWindow() + ")" : ""))
                .updatedAt(Instant.now())
                .updatedBy(performedBy)
                .build());
        orderRepository.save(order);

        // Realtime notify tới khách
        try {
            notificationClient.notifyOrderRescheduled(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getCustomerId(),
                    finalReason,
                    req.getNextDeliveryDate(),
                    newOrderRescheduleCount,
                    req.getPreferredTimeWindow(),
                    req.getNote());
        } catch (Exception ex) {
            log.warn("[DeliveryTrip] notify reschedule failed: {}", ex.getMessage());
        }

        // Cập nhật trip snapshot
        t.setRescheduleCount(t.getRescheduleCount() + 1);
        t.setLastRescheduleReason(finalReason);
        t.setNextDeliveryDate(req.getNextDeliveryDate());
        t.setLastRescheduleAt(Instant.now());

        DeliveryActionResult result = finalizeTripAfterAction(t, performedBy,
                "Hoãn giao đơn " + order.getOrderNumber() + " — " + finalReason);
        result.setWarning(newOrderRescheduleCount >= 3);
        result.setWarningMessage(newOrderRescheduleCount >= 3
                ? "Khách đã hoãn " + newOrderRescheduleCount + " lần — cần CSKH can thiệp."
                : null);
        return result;
    }

    /**
     * Bước 4': Tài xế xác nhận giao THẤT BẠI (khách từ chối dứt khoát, địa chỉ
     * không tiếp cận, v.v.). Khác với "không liên lạc được" (UNREACHABLE) ở chỗ
     * đây là quyết định cuối cùng.
     *  - link.deliveryState = FAILED
     *  - order.status: SHIPPING → RETURNED
     *  - Không thể tạo lại — CSKH xử lý thủ công.
     */
    public DeliveryActionResult markOrderFailed(String tripId, String orderId,
                                                MarkOrderFailedRequest req, String performedBy) {
        if (req.getReason() == RescheduleReason.OTHER
                && (req.getCustomReason() == null || req.getCustomReason().isBlank())) {
            throw new IllegalArgumentException("Khi chọn lý do KHÁC, vui lòng nhập mô tả cụ thể.");
        }

        DeliveryTrip t = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("DeliveryTrip not found: " + tripId));
        if (t.getStatus() != DeliveryTripStatus.SHIPPING
                && t.getStatus() != DeliveryTripStatus.ARRIVED
                && t.getStatus() != DeliveryTripStatus.RESCHEDULED) {
            throw new IllegalStateException(
                    "Chuyến ở trạng thái " + t.getStatus() + " — không thể đánh dấu thất bại.");
        }

        DeliveryTripOrder link = tripOrderRepository.findByTripIdAndOrderId(tripId, orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order " + orderId + " không thuộc chuyến " + tripId));
        if (link.getDeliveryState() == OrderDeliveryState.DELIVERED) {
            throw new IllegalStateException("Đơn đã giao thành công, không thể đánh dấu thất bại.");
        }
        if (link.getDeliveryState() == OrderDeliveryState.RESCHEDULED) {
            throw new IllegalStateException("Đơn đã được đánh dấu HOÃN — không thể đánh dấu thất bại.");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        String finalReason = req.getReason() == RescheduleReason.OTHER
                ? req.getCustomReason()
                : req.getReason().name();

        link.setDeliveryState(OrderDeliveryState.FAILED);
        link.setRescheduleReason(req.getReason());
        link.setDeliveryNote(req.getNote());
        link.setProofPhotoUrl(req.getProofPhotoUrl());
        link.setDeliveredAt(Instant.now());
        tripOrderRepository.save(link);

        order.setStatus(OrderStatus.RETURNED);
        order.addStatusHistory(OrderStatus.RETURNED.name(), performedBy,
                "Giao thất bại: " + finalReason + (req.getNote() != null ? ". " + req.getNote() : ""));
        order.addTrackingUpdate(TrackingUpdate.builder()
                .status("Giao thất bại")
                .location("Địa điểm khách hàng")
                .note("Lý do: " + finalReason)
                .updatedAt(Instant.now())
                .updatedBy(performedBy)
                .build());
        orderRepository.save(order);

        return finalizeTripAfterAction(t, performedBy, "Giao thất bại đơn " + order.getOrderNumber());
    }

    /**
     * Helper: sau khi tài xế xử lý xong 1 đơn, kiểm tra xem tất cả đơn trong
     * chuyến đã xong chưa. Nếu rồi → trip COMPLETED. Nếu toàn bộ đơn còn lại
     * đều RESCHEDULED/FAILED → trip RESCHEDULED.
     */
    private DeliveryActionResult finalizeTripAfterAction(DeliveryTrip t, String performedBy, String logMsg) {
        List<DeliveryTripOrder> links = tripOrderRepository.findByTripIdOrderBySequenceAsc(t.getId());
        int delivered = 0, rescheduled = 0, failed = 0, pending = 0, unreachable = 0;
        for (DeliveryTripOrder l : links) {
            OrderDeliveryState s = l.getDeliveryState() == null
                    ? OrderDeliveryState.PENDING : l.getDeliveryState();
            switch (s) {
                case DELIVERED: delivered++; break;
                case RESCHEDULED: rescheduled++; break;
                case FAILED: failed++; break;
                case UNREACHABLE: unreachable++; break;
                default: pending++;
            }
        }
        int total = links.size();
        int finished = delivered + rescheduled + failed;

        DeliveryTripStatus newStatus = t.getStatus();
        if (finished == total) {
            newStatus = DeliveryTripStatus.COMPLETED;
            t.setCompletedAt(Instant.now());
        } else if (delivered == 0 && rescheduled > 0 && (pending + unreachable) == 0) {
            // Không còn đơn nào có thể giao tiếp (tất cả hoãn hoặc fail) nhưng không có delivered.
            newStatus = DeliveryTripStatus.RESCHEDULED;
        }
        t.setStatus(newStatus);
        tripRepository.save(t);
        log.info("[DeliveryTrip] {} action by {}: {} | trip -> {} (delivered={}, rescheduled={}, failed={}, pending={}, unreachable={})",
                t.getTripNumber(), performedBy, logMsg, newStatus,
                delivered, rescheduled, failed, pending, unreachable);

        return DeliveryActionResult.builder()
                .tripStatus(newStatus.name())
                .orderStatus("UPDATED")
                .warning(false)
                .warningMessage(null)
                .totalOrders(total)
                .deliveredCount(delivered)
                .rescheduledCount(rescheduled)
                .failedCount(failed)
                .build();
    }

    // ───────────────────────────────────────────────────────────────────────
    // BULK ACTIONS
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Bắt đầu giao nhiều chuyến cùng lúc.
     */
    public BulkActionResponse bulkStartShipping(List<String> tripIds, String performedBy) {
        BulkActionResponse.BulkActionResponseBuilder builder = BulkActionResponse.builder();
        int success = 0, fail = 0;
        List<String> successIds = new ArrayList<>();
        List<String> failureIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String tripId : tripIds) {
            try {
                startShipping(tripId, performedBy);
                successIds.add(tripId);
                success++;
            } catch (Exception e) {
                failureIds.add(tripId);
                errors.add(tripId + ": " + e.getMessage());
                fail++;
            }
        }

        return builder
                .successCount(success)
                .failureCount(fail)
                .successIds(successIds)
                .failureIds(failureIds)
                .errors(errors)
                .build();
    }

    /**
     * Hủy nhiều chuyến cùng lúc.
     */
    public BulkActionResponse bulkCancelTrips(List<String> tripIds, String reason, String performedBy) {
        BulkActionResponse.BulkActionResponseBuilder builder = BulkActionResponse.builder();
        int success = 0, fail = 0;
        List<String> successIds = new ArrayList<>();
        List<String> failureIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String tripId : tripIds) {
            try {
                cancelTrip(tripId, reason, performedBy);
                successIds.add(tripId);
                success++;
            } catch (Exception e) {
                failureIds.add(tripId);
                errors.add(tripId + ": " + e.getMessage());
                fail++;
            }
        }

        return builder
                .successCount(success)
                .failureCount(fail)
                .successIds(successIds)
                .failureIds(failureIds)
                .errors(errors)
                .build();
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
                .assemblerIds(t.getAssemblerIds())
                .assemblerNames(t.getAssemblerNames())
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
                .completedAt(t.getCompletedAt())
                .cancelledAt(t.getCancelledAt())
                .cancelledBy(t.getCancelledBy())
                .cancelReason(t.getCancelReason())
                .arrivedAt(t.getArrivedAt())
                .rescheduleCount(t.getRescheduleCount())
                .lastRescheduleReason(t.getLastRescheduleReason())
                .nextDeliveryDate(t.getNextDeliveryDate())
                .lastRescheduleAt(t.getLastRescheduleAt());

        if (withOrders) {
            List<DeliveryTripOrder> links = tripOrderRepository.findByTripIdOrderBySequenceAsc(t.getId());
            List<TripOrderResponse> orders = new ArrayList<>();
            int delivered = 0, rescheduled = 0, failed = 0, pending = 0;
            for (DeliveryTripOrder link : links) {
                Order o = orderRepository.findById(link.getOrderId()).orElse(null);
                if (o == null) continue;

                OrderDeliveryState ds = link.getDeliveryState() == null
                        ? OrderDeliveryState.PENDING : link.getDeliveryState();
                switch (ds) {
                    case DELIVERED: delivered++; break;
                    case RESCHEDULED: rescheduled++; break;
                    case FAILED: failed++; break;
                    default: pending++;
                }

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
                        .deliveryState(ds.name())
                        .rescheduleReason(link.getRescheduleReason() != null
                                ? link.getRescheduleReason().name() : null)
                        .deliveryNote(link.getDeliveryNote())
                        .rescheduleCount(link.getRescheduleCount())
                        .nextDeliveryDate(link.getNextDeliveryDate())
                        .deliveredAt(link.getDeliveredAt())
                        .orderCreatedAt(o.getCreatedAt())
                        .build());
            }
            b.orders(orders);
            b.deliveredCount(delivered);
            b.rescheduledCount(rescheduled);
            b.failedCount(failed);
            b.pendingCount(pending);
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
