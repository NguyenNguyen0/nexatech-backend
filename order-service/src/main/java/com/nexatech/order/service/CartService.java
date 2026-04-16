package com.nexatech.order.service;

import com.nexatech.order.client.InventoryClient;
import com.nexatech.order.client.ProductClient;
import com.nexatech.order.client.dto.ProductInfo;
import com.nexatech.order.client.dto.StockOperationRequest;
import com.nexatech.order.dto.*;
import com.nexatech.order.exception.BadRequestException;
import com.nexatech.order.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private static final String CART_PREFIX = "cart:";
    private static final Duration CART_TTL = Duration.ofDays(30);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    public CartResponse getCart(UUID userId) {
        CartDto cart = loadCart(userId);
        return toResponse(cart);
    }

    public CartResponse addItem(UUID userId, AddToCartRequest request) {
        ProductInfo product = getValidProduct(request.getProductId());
        validateStock(request.getProductId(), request.getQuantity());

        CartDto cart = loadCart(userId);
        CartDto.CartItemDto existing = findItem(cart, request.getProductId());

        if (existing != null) {
            int newQty = existing.getQuantity() + request.getQuantity();
            validateStock(request.getProductId(), newQty);
            existing.setQuantity(newQty);
            existing.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(newQty)));
            existing.setProductName(product.getName());
            existing.setUnitPrice(product.getPrice());
            existing.setImageUrl(product.getImageUrl());
        } else {
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));
            CartDto.CartItemDto item = CartDto.CartItemDto.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .imageUrl(product.getImageUrl())
                    .unitPrice(product.getPrice())
                    .quantity(request.getQuantity())
                    .subtotal(subtotal)
                    .build();
            cart.getItems().add(item);
        }

        saveCart(userId, cart);
        log.info("Added product {} to cart of user {}", request.getProductId(), userId);
        return toResponse(cart);
    }

    public CartResponse updateItemQuantity(UUID userId, String productId, UpdateCartItemRequest request) {
        CartDto cart = loadCart(userId);
        CartDto.CartItemDto item = findItem(cart, productId);

        if (item == null) {
            throw new ResourceNotFoundException("Cart item not found: " + productId);
        }

        validateStock(productId, request.getQuantity());

        item.setQuantity(request.getQuantity());
        item.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(request.getQuantity())));

        saveCart(userId, cart);
        log.info("Updated quantity of product {} to {} for user {}", productId, request.getQuantity(), userId);
        return toResponse(cart);
    }

    public CartResponse removeItem(UUID userId, String productId) {
        CartDto cart = loadCart(userId);
        boolean removed = cart.getItems().removeIf(i -> i.getProductId().equals(productId));

        if (!removed) {
            throw new ResourceNotFoundException("Cart item not found: " + productId);
        }

        saveCart(userId, cart);
        log.info("Removed product {} from cart of user {}", productId, userId);
        return toResponse(cart);
    }

    public void clearCart(UUID userId) {
        redisTemplate.delete(cartKey(userId));
        log.info("Cleared cart of user {}", userId);
    }

    CartDto loadCart(UUID userId) {
        Object raw = redisTemplate.opsForValue().get(cartKey(userId));
        if (raw instanceof CartDto dto) {
            return dto;
        }
        return CartDto.builder()
                .userId(userId)
                .items(new ArrayList<>())
                .build();
    }

    private void saveCart(UUID userId, CartDto cart) {
        cart.setUpdatedAt(Instant.now());
        redisTemplate.opsForValue().set(cartKey(userId), cart, CART_TTL);
    }

    private String cartKey(UUID userId) {
        return CART_PREFIX + userId;
    }

    private CartDto.CartItemDto findItem(CartDto cart, String productId) {
        return cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElse(null);
    }

    private ProductInfo getValidProduct(String productId) {
        ProductInfo product = productClient.getProduct(productId).getData();
        if (product == null || !product.isActive()) {
            throw new BadRequestException("Sản phẩm không tồn tại hoặc đã ngừng bán: " + productId);
        }
        return product;
    }

    private void validateStock(String productId, int quantity) {
        StockOperationRequest stockReq = StockOperationRequest.builder()
                .items(List.of(StockOperationRequest.Item.builder()
                        .productId(productId)
                        .quantity(quantity)
                        .build()))
                .build();
        Boolean inStock = inventoryClient.checkStock(stockReq).getData();
        if (!Boolean.TRUE.equals(inStock)) {
            throw new BadRequestException("Sản phẩm không đủ tồn kho: " + productId);
        }
    }

    private CartResponse toResponse(CartDto cart) {
        List<CartResponse.CartItemResponse> items = cart.getItems().stream()
                .map(i -> CartResponse.CartItemResponse.builder()
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .imageUrl(i.getImageUrl())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .subtotal(i.getSubtotal())
                        .build())
                .toList();

        BigDecimal totalAmount = items.stream()
                .map(CartResponse.CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalItems = items.stream()
                .mapToInt(CartResponse.CartItemResponse::getQuantity)
                .sum();

        return CartResponse.builder()
                .items(items)
                .totalItems(totalItems)
                .totalAmount(totalAmount)
                .updatedAt(cart.getUpdatedAt())
                .build();
    }
}
