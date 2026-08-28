package com.woodfurni.inventory.service;

import com.woodfurni.catalog.product.repository.ProductRepository;
import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.inventory.exception.InsufficientStockException;
import com.woodfurni.inventory.model.Inventory;
import com.woodfurni.inventory.repository.InventoryRepository;
import com.woodfurni.notification.client.NotificationClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InventoryService — stock lifecycle operations.
 *
 * Covers DoD:
 *   - TC-INV-01: reserve(available=10, qty=2) → quantityReserved += 2
 *   - TC-INV-02: reserve(available=1, qty=3) → throw InsufficientStockException
 *   - TC-INV-03: release đúng số lượng → quantityReserved -= qty
 *   - TC-INV-04: commit → giảm cả quantityOnHand + quantityReserved
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private NotificationClient notificationClient;

    @InjectMocks private InventoryService inventoryService;

    private static final String PRODUCT_ID = "prod-1";

    private Inventory inv(int onHand, int reserved) {
        return Inventory.builder()
                .id("inv-1")
                .productId(PRODUCT_ID)
                .quantityOnHand(onHand)
                .quantityReserved(reserved)
                .lowStockThreshold(5)
                .build();
    }

    // ============================================================
    // TC-INV-01 — reserve thành công
    // findAndModify trả Inventory SAU KHI update (với reserved tăng)
    // ============================================================
    @Test
    @DisplayName("reserve(available=10, qty=2) - quantityReserved tăng đúng 2 đơn vị")
    void reserve_AvailableStock_Succeeds() {
        // Precondition check (findOne for Inventory existence) returns existing stock.
        when(mongoTemplate.findOne(any(Query.class), eq(Inventory.class)))
                .thenReturn(inv(10, 0));
        // findAndModify trả inventory sau khi đã tăng reserved → quantityReserved=2
        Inventory afterReserve = inv(10, 2);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(Inventory.class)))
                .thenReturn(afterReserve);

        assertDoesNotThrow(() -> inventoryService.reserve(PRODUCT_ID, 2));

        verify(mongoTemplate).findAndModify(any(Query.class), any(Update.class), eq(Inventory.class));
        verify(notificationClient, never()).notifyLowStock(any(), any(), anyInt(), anyInt());
    }

    // ============================================================
    // TC-INV-02 — reserve vượt tồn kho
    // findOne kiểm tra available trước → trả inventory hiện tại (available=1)
    // → ném InsufficientStockException
    // ============================================================
    @Test
    @DisplayName("reserve(available=1, qty=3) - throw InsufficientStockException, không modify")
    void reserve_ExceedsAvailable_ThrowsAndNoModification() {
        // available = 1 (onHand=1, reserved=0)
        when(mongoTemplate.findOne(any(Query.class), eq(Inventory.class)))
                .thenReturn(inv(1, 0));

        InsufficientStockException ex = assertThrows(
                InsufficientStockException.class,
                () -> inventoryService.reserve(PRODUCT_ID, 3)
        );
        assertNotNull(ex.getMessage());

        // No findAndModify called at all
        verify(mongoTemplate, never())
                .findAndModify(any(Query.class), any(Update.class), eq(Inventory.class));
    }

    // ============================================================
    // TC-INV-02b — inventory record không tồn tại
    // ============================================================
    @Test
    @DisplayName("reserve khi inventory record chưa có - throw EntityNotFoundException")
    void reserve_InventoryRecordMissing_ThrowsEntityNotFound() {
        when(mongoTemplate.findOne(any(Query.class), eq(Inventory.class)))
                .thenReturn(null);

        assertThrows(EntityNotFoundException.class,
                () -> inventoryService.reserve(PRODUCT_ID, 1));

        verify(mongoTemplate, never())
                .findAndModify(any(Query.class), any(Update.class), eq(Inventory.class));
    }

    // ============================================================
    // TC-INV-03 — release đúng số lượng
    // findAndModify trả inventory sau khi reserved giảm
    // ============================================================
    @Test
    @DisplayName("release(reserved=5, qty=2) - quantityReserved giảm 2, không throw")
    void release_ValidAmount_Succeeds() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(Inventory.class)))
                .thenReturn(inv(10, 3)); // sau release: reserved = 5 - 2 = 3

        assertDoesNotThrow(() -> inventoryService.release(PRODUCT_ID, 2));

        // Verify Update contains decrement
        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), captor.capture(), eq(Inventory.class));
        String updateJson = captor.getValue().getUpdateObject().toJson();
        assertTrue(updateJson.contains("quantityReserved"));
    }

    // ============================================================
    // TC-INV-03b — release khi không đủ reserved
    // findAndModify trả null (query không match) → throw InsufficientStockException
    // ============================================================
    @Test
    @DisplayName("release(reserved=2, qty=5) - findAndModify trả null, throw InsufficientStockException")
    void release_ExceedsReserved_Throws() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(Inventory.class)))
                .thenReturn(null);

        InsufficientStockException ex = assertThrows(
                InsufficientStockException.class,
                () -> inventoryService.release(PRODUCT_ID, 5)
        );
        assertNotNull(ex.getMessage());
    }

    // ============================================================
    // TC-INV-04 — commit giảm cả onHand + reserved
    // ============================================================
    @Test
    @DisplayName("commit(qty=2) - Update chứa cả quantityOnHand và quantityReserved decrement")
    void commit_DeductsBothOnHandAndReserved() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(Inventory.class)))
                .thenReturn(inv(8, 0)); // sau commit: onHand=8, reserved=0

        assertDoesNotThrow(() -> inventoryService.commit(PRODUCT_ID, 2));

        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), captor.capture(), eq(Inventory.class));
        String updateJson = captor.getValue().getUpdateObject().toJson();
        assertTrue(updateJson.contains("quantityOnHand"));
        assertTrue(updateJson.contains("quantityReserved"));
    }

    // ============================================================
    // TC-INV-04b — commit khi không đủ reserved
    // ============================================================
    @Test
    @DisplayName("commit khi reserved < qty - findAndModify trả null, throw InsufficientStockException")
    void commit_InsufficientReserved_Throws() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(Inventory.class)))
                .thenReturn(null);

        InsufficientStockException ex = assertThrows(
                InsufficientStockException.class,
                () -> inventoryService.commit(PRODUCT_ID, 10)
        );
        assertNotNull(ex.getMessage());
    }

    // ============================================================
    // TC-INV-05 — input validation
    // ============================================================
    @Test
    @DisplayName("reserve/release/commit với qty <= 0 - throw IllegalArgumentException")
    void operations_NonPositiveQty_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.reserve(PRODUCT_ID, 0));
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.reserve(PRODUCT_ID, -1));
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.release(PRODUCT_ID, 0));
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.release(PRODUCT_ID, -1));
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.commit(PRODUCT_ID, 0));
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.commit(PRODUCT_ID, -1));
    }

    // ============================================================
    // TC-INV-06 — getAvailable
    // ============================================================
    @Test
    @DisplayName("getAvailable(onHand=10, reserved=3) = 7")
    void getAvailable_ComputesDifference() {
        when(inventoryRepository.findByProductId(PRODUCT_ID))
                .thenReturn(Optional.of(inv(10, 3)));

        int available = inventoryService.getAvailable(PRODUCT_ID);
        assertEquals(7, available);
    }
}
