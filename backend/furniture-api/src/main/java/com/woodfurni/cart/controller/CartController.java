package com.woodfurni.cart.controller;

import com.woodfurni.cart.dto.AddToCartRequest;
import com.woodfurni.cart.dto.CartResponse;
import com.woodfurni.cart.dto.UpdateCartItemRequest;
import com.woodfurni.cart.service.CartService;
import com.woodfurni.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Shopping cart controller.
 * All endpoints require CUSTOMER role (or any authenticated user).
 */
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Shopping cart management")
@SecurityRequirement(name = "bearerAuth")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user's cart",
               description = "Returns cart with refreshed prices from current product catalog. " +
                       "Supports CUSTOMER and ADMIN roles.")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = userDetails.getUsername();
        CartResponse cart = cartService.getOrCreateCart(userId);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add item to cart",
               description = "Validates product exists and is ACTIVE. Checks stock availability " +
                       "(read-only, no reservation). If product already in cart, quantities are accumulated.")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AddToCartRequest request) {
        String userId = userDetails.getUsername();
        CartResponse cart = cartService.addItem(userId, request.getProductId(), request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", cart));
    }

    @PutMapping("/items/{productId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update item quantity",
               description = "Set quantity to 0 to remove item from cart. Validates stock availability.")
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String productId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        String userId = userDetails.getUsername();
        CartResponse cart = cartService.updateItemQuantity(userId, productId, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success("Cart item updated", cart));
    }

    @DeleteMapping("/items/{productId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove item from cart")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String productId) {
        String userId = userDetails.getUsername();
        CartResponse cart = cartService.removeItem(userId, productId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", cart));
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Clear all items from cart")
    public ResponseEntity<ApiResponse<CartResponse>> clearCart(
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = userDetails.getUsername();
        CartResponse cart = cartService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared", cart));
    }
}
