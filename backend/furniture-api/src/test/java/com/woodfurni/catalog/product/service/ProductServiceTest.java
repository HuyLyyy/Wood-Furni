package com.woodfurni.catalog.product.service;

import com.woodfurni.catalog.category.repository.CategoryRepository;
import com.woodfurni.catalog.material.repository.MaterialRepository;
import com.woodfurni.catalog.product.dto.ProductRequest;
import com.woodfurni.catalog.product.dto.ProductResponse;
import com.woodfurni.catalog.product.enums.ProductEnvironment;
import com.woodfurni.catalog.product.enums.ProductStatus;
import com.woodfurni.catalog.product.model.Product;
import com.woodfurni.catalog.product.repository.ProductRepository;
import com.woodfurni.inventory.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProductService — focused on DoD requirements.
 *
 * Covers:
 *   - TC-PROD-01: publish (status=ACTIVE) without images → rejected
 *   - TC-PROD-02: create() with duplicate SKU → rejected
 *   - TC-PROD-03: create() with valid payload → persists, generates slug, inits inventory
 *   - TC-PROD-04: create() with missing categoryId → rejected
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private InventoryService inventoryService;

    @InjectMocks private ProductService productService;

    private static final String PRODUCT_ID = "prod-1";
    private static final String SKU = "TABLE-OAK-001";

    private ProductRequest baseRequest() {
        return ProductRequest.builder()
                .sku(SKU)
                .name("Bàn gỗ sồi")
                .categoryId("cat-1")
                .environment(ProductEnvironment.INDOOR)
                .price(new BigDecimal("2500000"))
                .images(List.of("https://cdn.example.com/img1.jpg"))
                .build();
    }

    // ============================================================
    // TC-PROD-01 — publish thiếu ảnh
    // ============================================================
    @Test
    @DisplayName("changeStatus ACTIVE khi product không có ảnh - throw IllegalArgumentException")
    void changeStatus_ActiveWithoutImages_Rejected() {
        Product product = Product.builder()
                .id(PRODUCT_ID)
                .sku(SKU)
                .name("Bàn gỗ sồi")
                .images(List.of())          // NO images
                .status(ProductStatus.DRAFT)
                .build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> productService.changeStatus(PRODUCT_ID, ProductStatus.ACTIVE)
        );
        assertTrue(ex.getMessage().toLowerCase().contains("image"));

        // MUST NOT have saved
        verify(productRepository, never()).save(any(Product.class));
    }

    // ============================================================
    // TC-PROD-01b — publish có ảnh → OK
    // ============================================================
    @Test
    @DisplayName("changeStatus ACTIVE khi product có ảnh - lưu thành công")
    void changeStatus_ActiveWithImages_Succeeds() {
        Product product = Product.builder()
                .id(PRODUCT_ID)
                .sku(SKU)
                .images(List.of("https://cdn.example.com/img1.jpg"))
                .status(ProductStatus.DRAFT)
                .build();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse resp = productService.changeStatus(PRODUCT_ID, ProductStatus.ACTIVE);

        assertNotNull(resp);
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals(ProductStatus.ACTIVE, captor.getValue().getStatus());
    }

    // ============================================================
    // TC-PROD-02 — create() duplicate SKU
    // ============================================================
    @Test
    @DisplayName("create với SKU đã tồn tại - throw IllegalArgumentException, không save")
    void create_DuplicateSku_Rejected() {
        when(productRepository.existsBySku(SKU)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> productService.create(baseRequest())
        );
        assertTrue(ex.getMessage().contains("SKU"));

        verify(productRepository, never()).save(any(Product.class));
        verify(inventoryService, never()).initForProduct(anyString());
    }

    // ============================================================
    // TC-PROD-03 — create() happy path
    // ============================================================
    @Test
    @DisplayName("create với payload hợp lệ - lưu, tạo slug, init inventory")
    void create_ValidRequest_PersistsAndInitsInventory() {
        when(productRepository.existsBySku(SKU)).thenReturn(false);
        when(categoryRepository.existsById("cat-1")).thenReturn(true);
        when(productRepository.existsBySlug(anyString())).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", PRODUCT_ID);
            return p;
        });

        ProductResponse resp = productService.create(baseRequest());

        assertNotNull(resp);
        assertEquals(PRODUCT_ID, resp.getId());
        assertEquals(ProductStatus.DRAFT, resp.getStatus());

        // Auto-generated slug from "Bàn gỗ sồi"
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product saved = captor.getValue();
        assertEquals("ban-go-soi", saved.getSlug());
        assertEquals(ProductStatus.DRAFT, saved.getStatus());
        assertEquals(0.0, saved.getRatingAverage());
        assertEquals(0, saved.getRatingCount());

        // Inventory record must be initialised
        verify(inventoryService).initForProduct(PRODUCT_ID);
    }

    // ============================================================
    // TC-PROD-04 — create() category không tồn tại
    // ============================================================
    @Test
    @DisplayName("create với categoryId không tồn tại - throw IllegalArgumentException")
    void create_MissingCategory_Rejected() {
        when(productRepository.existsBySku(SKU)).thenReturn(false);
        when(categoryRepository.existsById("cat-missing")).thenReturn(false);

        ProductRequest req = baseRequest();
        ReflectionTestUtils.setField(req, "categoryId", "cat-missing");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> productService.create(req)
        );
        assertTrue(ex.getMessage().contains("Category"));

        verify(productRepository, never()).save(any(Product.class));
        verify(inventoryService, never()).initForProduct(anyString());
    }
}
