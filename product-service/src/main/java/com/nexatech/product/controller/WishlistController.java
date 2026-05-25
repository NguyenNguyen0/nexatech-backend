package com.nexatech.product.controller;

import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.PageResponse;
import com.nexatech.product.dto.response.ProductMinimalResponse;
import com.nexatech.product.dto.response.WishlistActionResponse;
import com.nexatech.product.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping("/{productId}")
    public ApiResponse<WishlistActionResponse> addToWishlist(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable String productId) {
        boolean wishlisted = wishlistService.addToWishlist(userId, productId);
        return ApiResponse.ok(WishlistActionResponse.builder()
                .productId(productId)
                .wishlisted(wishlisted)
                .build());
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<WishlistActionResponse> removeFromWishlist(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable String productId) {
        boolean wishlisted = wishlistService.removeFromWishlist(userId, productId);
        return ApiResponse.ok(WishlistActionResponse.builder()
                .productId(productId)
                .wishlisted(wishlisted)
                .build());
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductMinimalResponse>> getWishlist(
            @RequestHeader("X-User-Id") UUID userId,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(wishlistService.getWishlist(userId, pageable)));
    }
}
