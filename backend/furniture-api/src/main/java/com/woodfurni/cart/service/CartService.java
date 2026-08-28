package com.woodfurni.cart.service;

import com.woodfurni.cart.dto.CartResponse;
import com.woodfurni.cart.model.Cart;
import com.woodfurni.cart.model.CartItem;
import com.woodfurni.cart.repository.CartRepository;
import com.woodfurni.catalog.product.enums.ProductStatus;
import com.woodfurni.catalog.product.model.Product;
import com.woodfurni.catalog.product.repository.ProductRepository;
import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.inventory.model.Inventory;
import com.woodfurni.inventory.repository.InventoryRepository;
import com.woodfurni.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Shopping cart service.
 *
 * Business rules:
 * - 1 cart per user (auto-created on first access)
 * - Prices are NOT stored long-term in cart items. On every GET /cart, we refresh
 *   product prices (price + salePrice) from the product catalog. This ensures the
 *   cart always shows current prices without needing historical price tracking.
 * - Inventory is NOT reserved at add-to-cart time — only validated for availability.
 *   Actual reservation happens during checkout (Order module).
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;

    /**
     * Get or create cart for user.
     */
    @Transactional
    public CartResponse getOrCreateCart(String userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .userId(userId)
                            .items(new ArrayList<>())
                            .totalAmount(BigDecimal.ZERO)
                            .build();
                    return cartRepository.save(newCart);
                });
        return refreshAndBuildResponse(cart);
    }

    /**
     * Add item to cart. If product already in cart, accumulate quantity.
     * Validates:
     * - Product exists and is ACTIVE
     * - Requested quantity <= available stock (read-only check, no reservation)
     */
    @Transactional
    public CartResponse addItem(String userId, String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new IllegalArgumentException("Product is not available for purchase");
        }

        // Only enforce stock check when inventory record exists.
        // Products without inventory tracking can still be added to cart.
        int available = 0;
        boolean hasInventory = false;
        Inventory inv = inventoryRepository.findByProductId(productId).orElse(null);
        if (inv != null) {
            hasInventory = true;
            available = inv.getQuantityOnHand() - inv.getQuantityReserved();
            if (quantity > available) {
                throw new IllegalArgumentException(
                        String.format("Not enough stock. Requested: %d, Available: %d", quantity, available));
            }
        }

        Cart cart = getOrCreateCartEntity(userId);

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst();

        BigDecimal unitPrice = resolveUnitPrice(product);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            int newQuantity = item.getQuantity() + quantity;
            if (hasInventory && newQuantity > available) {
                throw new IllegalArgumentException(
                        String.format("Total quantity exceeds available stock. Requested total: %d, Available: %d",
                                newQuantity, available));
            }
            item.setQuantity(newQuantity);
            item.setUnitPrice(unitPrice);
            item.setProductName(product.getName());
            item.setProductSlug(product.getSlug());
            item.setProductImage(product.getImages() != null && !product.getImages().isEmpty()
                    ? product.getImages().get(0) : null);
            item.calculateSubtotal();
        } else {
            CartItem newItem = CartItem.builder()
                    .productId(productId)
                    .productName(product.getName())
                    .productSlug(product.getSlug())
                    .productImage(product.getImages() != null && !product.getImages().isEmpty()
                            ? product.getImages().get(0) : null)
                    .unitPrice(unitPrice)
                    .quantity(quantity)
                    .build();
            newItem.calculateSubtotal();
            cart.getItems().add(newItem);
        }

        recalculateTotal(cart);
        cartRepository.save(cart);
        return refreshAndBuildResponse(cart);
    }

    /**
     * Update item quantity. If quantity=0, remove item.
     */
    @Transactional
    public CartResponse updateItemQuantity(String userId, String productId, int quantity) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Cart not found for user: " + userId));

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst();

        if (existingItem.isEmpty()) {
            throw new EntityNotFoundException("Item not found in cart: " + productId);
        }

        if (quantity <= 0) {
            cart.getItems().remove(existingItem.get());
        } else {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new IllegalArgumentException("Product is not available for purchase");
            }

            // Only enforce stock check when inventory record exists.
            Inventory inv = inventoryRepository.findByProductId(productId).orElse(null);
            if (inv != null) {
                int available = inv.getQuantityOnHand() - inv.getQuantityReserved();
                if (quantity > available) {
                    throw new IllegalArgumentException(
                            String.format("Not enough stock. Requested: %d, Available: %d", quantity, available));
                }
            }

            CartItem item = existingItem.get();
            item.setQuantity(quantity);
            item.setUnitPrice(resolveUnitPrice(product));
            item.setProductName(product.getName());
            item.setProductSlug(product.getSlug());
            item.calculateSubtotal();
        }

        recalculateTotal(cart);
        cartRepository.save(cart);
        return refreshAndBuildResponse(cart);
    }

    /**
     * Remove single item from cart.
     */
    @Transactional
    public CartResponse removeItem(String userId, String productId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Cart not found for user: " + userId));

        boolean removed = cart.getItems().removeIf(item -> item.getProductId().equals(productId));

        if (!removed) {
            throw new EntityNotFoundException("Item not found in cart: " + productId);
        }

        recalculateTotal(cart);
        cartRepository.save(cart);
        return refreshAndBuildResponse(cart);
    }

    /**
     * Clear all items from cart.
     */
    @Transactional
    public CartResponse clearCart(String userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Cart not found for user: " + userId));

        cart.setItems(new ArrayList<>());
        cart.setTotalAmount(BigDecimal.ZERO);
        cartRepository.save(cart);

        return buildResponse(cart);
    }

    private Cart getOrCreateCartEntity(String userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .userId(userId)
                            .items(new ArrayList<>())
                            .totalAmount(BigDecimal.ZERO)
                            .build();
                    return cartRepository.save(newCart);
                });
    }

    private BigDecimal resolveUnitPrice(Product product) {
        if (product.getSalePrice() != null) {
            return product.getSalePrice();
        }
        return product.getPrice();
    }

    /**
     * Recalculate totalAmount from item subtotals.
     * Called after every cart mutation.
     */
    private void recalculateTotal(Cart cart) {
        cart.calculateTotalAmount();
    }

    /**
     * Build response with refreshed prices from product catalog.
     * This is the "price snapshot refresh" logic: every GET returns current prices.
     */
    private CartResponse refreshAndBuildResponse(Cart cart) {
        if (cart.getItems().isEmpty()) {
            return buildResponse(cart);
        }

        List<String> productIds = cart.getItems().stream()
                .map(CartItem::getProductId)
                .collect(Collectors.toList());

        List<Product> products = productRepository.findAllById(productIds);

        for (CartItem item : cart.getItems()) {
            products.stream()
                    .filter(p -> p.getId().equals(item.getProductId()))
                    .findFirst()
                    .ifPresent(product -> {
                        item.setProductName(product.getName());
                        item.setProductSlug(product.getSlug());
                        item.setProductImage(
                                product.getImages() != null && !product.getImages().isEmpty()
                                        ? product.getImages().get(0) : null);
                        item.setUnitPrice(resolveUnitPrice(product));
                        item.calculateSubtotal();
                    });
        }

        recalculateTotal(cart);
        return buildResponse(cart);
    }

    private CartResponse buildResponse(Cart cart) {
        List<CartResponse.CartItemResponse> itemResponses = cart.getItems().stream()
                .map(item -> CartResponse.CartItemResponse.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .productSlug(item.getProductSlug())
                        .productImage(item.getProductImage())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        int itemCount = cart.getItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();

        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .items(itemResponses)
                .totalAmount(cart.getTotalAmount())
                .itemCount(itemCount)
                .updatedAt(cart.getUpdatedAt())
                .build();
    }
}
