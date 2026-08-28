package com.woodfurni.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.woodfurni.auth.enums.Role;
import com.woodfurni.auth.enums.UserStatus;
import com.woodfurni.auth.model.Address;
import com.woodfurni.auth.model.User;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.cart.model.Cart;
import com.woodfurni.cart.model.CartItem;
import com.woodfurni.cart.repository.CartRepository;
import com.woodfurni.catalog.product.enums.ProductEnvironment;
import com.woodfurni.catalog.product.model.Product;
import com.woodfurni.catalog.product.repository.ProductRepository;
import com.woodfurni.inventory.model.Inventory;
import com.woodfurni.inventory.repository.InventoryRepository;
import com.woodfurni.order.dto.CheckoutRequest;
import com.woodfurni.order.enums.PaymentMethod;
import com.woodfurni.order.model.Order;
import com.woodfurni.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full checkout flow integration test (TC-ORDER-01, TC-ORDER-02).
 *
 * Runs the real Spring Boot context with embedded MongoDB (de.flapdoodle),
 * real services, and {@code MockMvc}. Spring Security Test's
 * {@link WithMockUser} injects the authenticated principal so
 * {@code @AuthenticationPrincipal} resolves correctly.
 *
 *   TC-ORDER-01: Given user, product, inventory, cart
 *                 When POST /api/v1/orders/checkout (sandbox card)
 *                 Then Order CONFIRMED, paymentStatus PAID,
 *                      Inventory.quantityReserved += cart qty,
 *                      Cart.items empty
 *
 *   TC-ORDER-02: Given available stock < requested qty
 *                 When POST /api/v1/orders/checkout
 *                 Then HTTP 4xx, no order, inventory unchanged
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User customer;
    private Product product;
    private Inventory inventory;
    private Address address;
    private Cart cart;

    @BeforeEach
    void setUp() {
        // Clean slate
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        // Build address embedded inside the user (per spec Mục 3.2)
        address = Address.builder()
                .id("addr-001")
                .label("Nhà riêng")
                .line1("123 Nguyễn Huệ")
                .ward("Phường Bến Nghé")
                .district("Quận 1")
                .city("TP.HCM")
                .phone("0900000001")
                .isDefault(true)
                .build();

        customer = User.builder()
                .email("customer-it@example.com")
                .passwordHash(passwordEncoder.encode("Pass#1234"))
                .fullName("Khách hàng IT")
                .phone("0900000001")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .addresses(new ArrayList<>(List.of(address)))
                .build();
        customer = userRepository.save(customer);

        product = Product.builder()
                .sku("CHAIR-OAK-IT-001")
                .slug("ghe-oak-it-001")
                .name("Ghế gỗ sồi")
                .categoryId("cat-1")
                .environment(ProductEnvironment.INDOOR)
                .price(new BigDecimal("500000"))
                .images(List.of("https://cdn.example.com/img1.jpg"))
                .status(com.woodfurni.catalog.product.enums.ProductStatus.ACTIVE)
                .build();
        product = productRepository.save(product);

        inventory = Inventory.builder()
                .productId(product.getId())
                .quantityOnHand(10)
                .quantityReserved(0)
                .lowStockThreshold(5)
                .build();
        inventory = inventoryRepository.save(inventory);

        CartItem item = CartItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .unitPrice(product.getPrice())
                .quantity(2)
                .subtotal(product.getPrice().multiply(BigDecimal.valueOf(2)))
                .build();
        cart = Cart.builder()
                .userId(customer.getId())
                .items(new ArrayList<>(List.of(item)))
                .totalAmount(product.getPrice().multiply(BigDecimal.valueOf(2)))
                .build();
        cart = cartRepository.save(cart);
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ============================================================
    // TC-ORDER-01 — checkout sandbox card thành công
    // ============================================================
    @Test
    @WithMockUser(username = "ignored", roles = "CUSTOMER")
    @DisplayName("TC-ORDER-01: Checkout sandbox - Order CONFIRMED, reserved += 2, cart rỗng")
    void checkout_SandboxCard_Succeeds() throws Exception {
        // Override the @WithMockUser username with the actual seeded customer id
        // (the controller reads userId from UserDetails.getUsername()).
        // We re-secure via SecurityMockMvcRequestPostProcessors.user() below.

        CheckoutRequest req = CheckoutRequest.builder()
                .addressId(address.getId())
                .paymentMethod(PaymentMethod.SANDBOX_CARD)
                .build();

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(customer.getId()).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.orderNumber").exists());

        // Order persisted
        List<Order> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        Order saved = orders.get(0);
        assertEquals(customer.getId(), saved.getCustomerId());
        assertTrue(saved.getOrderNumber().startsWith("ORD-"));

        // Inventory reserved (not committed — that happens on DELIVERED)
        Inventory reloaded = inventoryRepository.findById(inventory.getId()).orElseThrow();
        assertEquals(10, reloaded.getQuantityOnHand(), "quantityOnHand unchanged");
        assertEquals(2, reloaded.getQuantityReserved(), "quantityReserved must be 2 after checkout");

        // Cart cleared
        Cart reloadedCart = cartRepository.findByUserId(customer.getId()).orElseThrow();
        assertTrue(reloadedCart.getItems().isEmpty());
        assertEquals(0, reloadedCart.getTotalAmount().compareTo(BigDecimal.ZERO));
    }

    // ============================================================
    // TC-ORDER-02 — hết tồn kho → reject
    // ============================================================
    @Test
    @DisplayName("TC-ORDER-02: Checkout yêu cầu 3 nhưng chỉ còn 1 - 4xx, không tạo order")
    void checkout_InsufficientStock_Rejected() throws Exception {
        // Drain inventory to 1 unit, cart requests 3
        inventory.setQuantityOnHand(1);
        inventoryRepository.save(inventory);

        cart.getItems().get(0).setQuantity(3);
        cart.getItems().get(0).setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(3)));
        cart.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(3)));
        cartRepository.save(cart);

        CheckoutRequest req = CheckoutRequest.builder()
                .addressId(address.getId())
                .paymentMethod(PaymentMethod.SANDBOX_CARD)
                .build();

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(customer.getId()).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());

        // NO order created
        assertTrue(orderRepository.findAll().isEmpty(),
                "No order should be created when stock is insufficient");

        // Inventory unchanged
        Inventory reloaded = inventoryRepository.findById(inventory.getId()).orElseThrow();
        assertEquals(1, reloaded.getQuantityOnHand());
        assertEquals(0, reloaded.getQuantityReserved());
    }
}
