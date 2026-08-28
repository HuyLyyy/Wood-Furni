package com.woodfurni.inventory.service;

import com.woodfurni.catalog.product.model.Product;
import com.woodfurni.catalog.product.repository.ProductRepository;
import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.inventory.dto.InventoryAdjustRequest;
import com.woodfurni.inventory.dto.InventoryHistoryResponse;
import com.woodfurni.inventory.dto.InventoryResponse;
import com.woodfurni.inventory.exception.InsufficientStockException;
import com.woodfurni.inventory.model.Inventory;
import com.woodfurni.inventory.model.InventoryHistory;
import com.woodfurni.inventory.repository.InventoryHistoryRepository;
import com.woodfurni.inventory.repository.InventoryRepository;
import com.woodfurni.common.PageResponse;
import com.woodfurni.notification.client.NotificationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

/**
 * Inventory management service.
 *
 * Provides atomic stock operations for order processing:
 * - reserve(): hold stock during checkout (prevents overselling)
 * - release(): undo reservation on cart abandon/cancel
 * - commit(): finalize deduction after successful delivery
 * - adjust(): manual stock corrections (restock, damage, count)
 *
 * All mutations use findAndModify for atomicity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    private final InventoryRepository inventoryRepository;
    private final InventoryHistoryRepository historyRepository;
    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;
    private final NotificationClient notificationClient;

    /**
     * Resolve a product's display name. Best effort — returns null on miss so
     * the notification can still fire (we never want a missing product name
     * to silently swallow the low-stock alert).
     */
    private String resolveProductName(String productId) {
        if (productId == null) return null;
        return productRepository.findById(productId).map(Product::getName).orElse(null);
    }

    /**
     * Fire a low-stock notification if quantityOnHand is at or below threshold.
     * Only emits when the crossing is meaningful (i.e. now below threshold).
     */
    private void maybeNotifyLowStock(Inventory inventory, int previousOnHand) {
        if (inventory == null) return;
        int currentOnHand = inventory.getQuantityOnHand();
        int threshold = inventory.getLowStockThreshold() == null ? 5 : inventory.getLowStockThreshold();

        // Only alert when crossing downward (or already below). Don't spam on every adjust.
        boolean belowNow = currentOnHand <= threshold;
        if (!belowNow) return;

        notificationClient.notifyLowStock(
                inventory.getProductId(),
                resolveProductName(inventory.getProductId()),
                currentOnHand,
                threshold);
    }

    /**
     * Initialize inventory record for a newly created product.
     * Called from ProductService.create() to ensure every product has stock tracking.
     */
    public Inventory initForProduct(String productId) {
        if (inventoryRepository.existsByProductId(productId)) {
            return inventoryRepository.findByProductId(productId).orElse(null);
        }

        Inventory inventory = Inventory.builder()
                .productId(productId)
                .quantityOnHand(0)
                .quantityReserved(0)
                .lowStockThreshold(5)
                .build();

        return inventoryRepository.save(inventory);
    }

    /**
     * Get quantity available (quantityOnHand - quantityReserved).
     * Returns 0 if no inventory record exists yet (untracked product).
     */
    public int getAvailable(String productId) {
        Inventory inv = inventoryRepository.findByProductId(productId).orElse(null);
        if (inv == null) {
            return 0;
        }
        return inv.getQuantityOnHand() - inv.getQuantityReserved();
    }

    /**
     * Get full inventory record by product ID.
     */
    public InventoryResponse getByProductId(String productId) {
        Inventory inv = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new EntityNotFoundException("Inventory not found for product: " + productId));
        return toResponse(inv, null, null);
    }

    /**
     * List all inventory records with pagination.
     * Includes product name and SKU from join.
     */
    public PageResponse<InventoryResponse> getAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Inventory> inventoryPage = inventoryRepository.findAll(pageable);
        return buildPageResponse(inventoryPage, pageable);
    }

    /**
     * List low-stock items (quantityOnHand <= lowStockThreshold).
     */
    public PageResponse<InventoryResponse> getLowStock(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Inventory> inventoryPage = inventoryRepository.findAllByQuantityOnHandLessThanEqual(5, pageable);
        return buildPageResponse(inventoryPage, pageable);
    }

    /**
     * Reserve stock for an order.
     * Atomically increments quantityReserved if sufficient available stock exists.
     * Auto-initializes inventory record if missing (treats as 0 on-hand → insufficient stock).
     *
     * @throws InsufficientStockException if available < qty
     */
    public void reserve(String productId, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive");
        }

        Query checkQuery = new Query(Criteria.where("productId").is(productId));
        Inventory current = mongoTemplate.findOne(checkQuery, Inventory.class);
        if (current == null) {
            // Auto-initialize: product exists but has no inventory record.
            // Treat as 0 on-hand → insufficient stock.
            throw new InsufficientStockException(productId, qty, 0);
        }

        if (current.getQuantityOnHand() - current.getQuantityReserved() < qty) {
            throw new InsufficientStockException(productId, qty, current.getQuantityOnHand() - current.getQuantityReserved());
        }

        Query reserveQuery = new Query(Criteria.where("productId").is(productId));
        Update update = new Update().inc("quantityReserved", qty);
        Inventory result = mongoTemplate.findAndModify(reserveQuery, update, Inventory.class);

        if (result == null) {
            throw new InsufficientStockException(productId, qty, 0);
        }
    }

    /**
     * Initialize an inventory record for a product if one does not already exist.
     * Safe to call multiple times — only inserts if absent.
     *
     * @param productId the product ID
     */
    public void initStockIfAbsent(String productId) {
        log.info("[InventoryService] initStockIfAbsent called for productId={}", productId);
        Query query = new Query(Criteria.where("productId").is(productId));
        Inventory existing = mongoTemplate.findOne(query, Inventory.class);
        if (existing == null) {
            Inventory inv = Inventory.builder()
                    .productId(productId)
                    .quantityOnHand(0)
                    .quantityReserved(0)
                    .lowStockThreshold(DEFAULT_LOW_STOCK_THRESHOLD)
                    .build();
            mongoTemplate.save(inv);
            log.info("[InventoryService] Created new inventory record for productId={}", productId);
        } else {
            log.info("[InventoryService] Inventory already exists for productId={}, qtyOnHand={}", productId, existing.getQuantityOnHand());
        }
    }

    /**
     * Release previously reserved stock.
     * Called when order is cancelled or cart item is removed.
     * Silently succeeds if no inventory record exists (nothing to release).
     */
    public void release(String productId, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Release quantity must be positive");
        }

        Query checkQuery = new Query(Criteria.where("productId").is(productId));
        Inventory current = mongoTemplate.findOne(checkQuery, Inventory.class);
        if (current == null || current.getQuantityReserved() < qty) {
            throw new InsufficientStockException(
                    "Cannot release " + qty + " units for product " + productId +
                    ": insufficient reserved quantity");
        }

        Query query = new Query(Criteria.where("productId").is(productId)
                .and("quantityReserved").gte(qty));

        Update update = new Update().inc("quantityReserved", -qty);
        Inventory result = mongoTemplate.findAndModify(query, update, Inventory.class);

        if (result == null) {
            throw new InsufficientStockException(
                    "Cannot release " + qty + " units for product " + productId +
                    ": insufficient reserved quantity");
        }
    }

    /**
     * Commit reserved stock — finalize deduction after successful delivery.
     * Reduces both quantityOnHand and quantityReserved atomically.
     */
    public void commit(String productId, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Commit quantity must be positive");
        }

        Query checkQuery = new Query(Criteria.where("productId").is(productId));
        Inventory current = mongoTemplate.findOne(checkQuery, Inventory.class);
        if (current == null || current.getQuantityReserved() < qty) {
            throw new InsufficientStockException(
                    "Cannot commit " + qty + " units for product " + productId +
                    ": insufficient reserved quantity");
        }

        Query query = new Query(Criteria.where("productId").is(productId)
                .and("quantityReserved").gte(qty));

        Update update = new Update()
                .inc("quantityOnHand", -qty)
                .inc("quantityReserved", -qty);

        Inventory result = mongoTemplate.findAndModify(query, update, Inventory.class);

        if (result == null) {
            throw new InsufficientStockException(
                    "Cannot commit " + qty + " units for product " + productId +
                    ": insufficient reserved quantity");
        }

        // After delivery, quantityOnHand may have crossed the low-stock threshold.
        maybeNotifyLowStock(result, /*previousOnHand=*/ -1);
    }

    /**
     * Manually adjust stock for a product (restock or damage deduction).
     * Supports both additions (delta > 0, e.g., restock) and deductions (delta < 0, e.g., damage).
     * Prevents quantityOnHand from going negative.
     *
     * Writes an audit entry to the inventory history.
     *
     * @param productId target product
     * @param delta positive for restock, negative for deduction/damage
     * @param reason documentation of why adjustment was made
     * @param actorName human-readable label for the actor (e.g. "Lê Văn Kho - WAREHOUSE")
     * @param actorUserId ID of the user performing the action
     */
    public InventoryResponse adjust(String productId, int delta, String reason,
                                  String actorName, String actorUserId) {
        if (delta == 0) {
            throw new IllegalArgumentException("Delta cannot be zero");
        }

        ObjectId productIdObj = toObjectId(productId);
        if (productIdObj == null) {
            throw new EntityNotFoundException("Invalid productId format: " + productId);
        }

        Query query;
        Update update;

        if (delta > 0) {
            query = new Query(Criteria.where("productId").is(productIdObj));
            update = new Update()
                    .inc("quantityOnHand", delta);
        } else {
            query = new Query(Criteria.where("productId").is(productIdObj)
                    .and("quantityOnHand").gte(-delta));
            update = new Update()
                    .inc("quantityOnHand", delta);
        }

        Inventory result = mongoTemplate.findAndModify(query, update, Inventory.class);

        if (result == null) {
            if (delta < 0) {
                Inventory current = inventoryRepository.findByProductId(productId).orElse(null);
                if (current == null) {
                    throw new EntityNotFoundException("Inventory not found for product: " + productId);
                }
                throw new InsufficientStockException(
                        "Cannot deduct " + (-delta) + " units: only " + current.getQuantityOnHand() + " available");
            }
            throw new EntityNotFoundException("Inventory not found for product: " + productId);
        }

        int previousOnHand = result.getQuantityOnHand();
        int newOnHand = previousOnHand + delta;

        // findAndModify returns the PRE-modification document. Update in-memory.
        result.setQuantityOnHand(newOnHand);

        // Write audit history.
        writeHistory(result.getId(), productId, delta, previousOnHand, newOnHand,
                actorName, actorUserId, reason, "MANUAL_ADJUST");

        maybeNotifyLowStock(result, previousOnHand);

        return toResponse(result, null, null);
    }

    /**
     * Convert a string productId to MongoDB ObjectId.
     * The inventories collection stores productId as ObjectId, not String.
     * Returns null if the string is not a valid 24-char hex ObjectId.
     */
    private ObjectId toObjectId(String id) {
        if (id == null || id.length() != 24) {
            return null;
        }
        try {
            return new ObjectId(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private PageResponse<InventoryResponse> buildPageResponse(Page<Inventory> inventoryPage, Pageable pageable) {
        List<Inventory> inventories = inventoryPage.getContent();

        List<String> productIds = inventories.stream()
                .map(Inventory::getProductId)
                .collect(Collectors.toList());

        Map<String, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<InventoryResponse> responses = inventories.stream()
                .map(inv -> {
                    Product product = productMap.get(inv.getProductId());
                    return toResponse(inv, product, null);
                })
                .collect(Collectors.toList());

        return PageResponse.<InventoryResponse>builder()
                .items(responses)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(inventoryPage.getTotalElements())
                .totalPages(inventoryPage.getTotalPages())
                .build();
    }

    private InventoryResponse toResponse(Inventory inv, Product product, Object placeholder) {
        String productName = null;
        String productSku = null;
        if (product != null) {
            productName = product.getName();
            productSku = product.getSku();
        } else if (inv.getProductId() != null) {
            Product p = productRepository.findById(inv.getProductId()).orElse(null);
            if (p != null) {
                productName = p.getName();
                productSku = p.getSku();
            }
        }

        int available = inv.getQuantityOnHand() - inv.getQuantityReserved();
        boolean isLowStock = inv.getQuantityOnHand() <= inv.getLowStockThreshold();

        return InventoryResponse.builder()
                .id(inv.getId())
                .productId(inv.getProductId())
                .productName(productName)
                .productSku(productSku)
                .quantityOnHand(inv.getQuantityOnHand())
                .quantityReserved(inv.getQuantityReserved())
                .quantityAvailable(available)
                .lowStockThreshold(inv.getLowStockThreshold())
                .isLowStock(isLowStock)
                .updatedAt(inv.getUpdatedAt())
                .build();
    }

    // ─── Inventory History ───────────────────────────────────────────────────────

    /**
     * Get paginated history for an inventory document (newest first).
     */
    public PageResponse<InventoryHistoryResponse> getHistoryByInventoryId(String inventoryId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<InventoryHistory> historyPage = historyRepository
                .findByInventoryIdOrderByCreatedAtDesc(inventoryId, pageable);
        return buildHistoryPageResponse(historyPage, pageable);
    }

    /**
     * Get paginated history for a product (newest first).
     */
    public PageResponse<InventoryHistoryResponse> getHistoryByProductId(String productId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<InventoryHistory> historyPage = historyRepository
                .findByProductIdOrderByCreatedAtDesc(productId, pageable);
        return buildHistoryPageResponse(historyPage, pageable);
    }

    /**
     * Write one history entry.
     * Called internally by adjust(), reserve(), release(), commit().
     */
    public void writeHistory(String inventoryId, String productId, int delta,
                            int previousQty, int newQty,
                            String actorName, String actorUserId,
                            String reason, String operationType) {
        InventoryHistory entry = InventoryHistory.builder()
                .inventoryId(inventoryId)
                .productId(productId)
                .delta(delta)
                .previousQuantity(previousQty)
                .newQuantity(newQty)
                .actorName(actorName)
                .actorUserId(actorUserId)
                .reason(reason)
                .operationType(operationType)
                .createdAt(Instant.now())
                .build();
        historyRepository.save(entry);
        log.info("[InventoryHistory] {} | productId={} | delta={} | {}→{} | actor={}",
                operationType, productId, delta, previousQty, newQty, actorName);
    }

    private PageResponse<InventoryHistoryResponse> buildHistoryPageResponse(
            Page<InventoryHistory> historyPage, Pageable pageable) {
        List<InventoryHistoryResponse> items = historyPage.getContent().stream()
                .map(this::toHistoryResponse)
                .collect(Collectors.toList());
        return PageResponse.<InventoryHistoryResponse>builder()
                .items(items)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(historyPage.getTotalElements())
                .totalPages(historyPage.getTotalPages())
                .build();
    }

    private InventoryHistoryResponse toHistoryResponse(InventoryHistory h) {
        return InventoryHistoryResponse.builder()
                .id(h.getId())
                .inventoryId(h.getInventoryId())
                .productId(h.getProductId())
                .delta(h.getDelta())
                .previousQuantity(h.getPreviousQuantity())
                .newQuantity(h.getNewQuantity())
                .actorName(h.getActorName())
                .actorUserId(h.getActorUserId())
                .reason(h.getReason())
                .operationType(h.getOperationType())
                .createdAt(h.getCreatedAt())
                .build();
    }
}
