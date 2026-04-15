package com.nexatech.order.controller;

import com.nexatech.order.dto.AddToCartRequest;
import com.nexatech.order.dto.CartResponse;
import com.nexatech.order.dto.UpdateCartItemRequest;
import com.nexatech.order.dto.response.ApiResponse;
import com.nexatech.order.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @RequestHeader("X-User-Id") UUID customerId) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.getCart(customerId)));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @RequestHeader("X-User-Id") UUID customerId,
            @Valid @RequestBody AddToCartRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.addItem(customerId, request)));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(
            @RequestHeader("X-User-Id") UUID customerId,
            @PathVariable String productId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.updateItemQuantity(customerId, productId, request)));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @RequestHeader("X-User-Id") UUID customerId,
            @PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.removeItem(customerId, productId)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @RequestHeader("X-User-Id") UUID customerId) {
        cartService.clearCart(customerId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Cart cleared"));
    }
}
