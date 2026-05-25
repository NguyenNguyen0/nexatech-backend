package com.nexatech.product.controller;

import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.PageResponse;
import com.nexatech.product.dto.response.ProductCatalogSyncResponse;
import com.nexatech.product.exception.ForbiddenException;
import com.nexatech.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/products")
@RequiredArgsConstructor
public class ProductInternalCatalogController {

    private final ProductService productService;

    @Value("${product.internal.api-key:change-me}")
    private String internalApiKey;

    @GetMapping("/all")
    public ApiResponse<PageResponse<ProductCatalogSyncResponse>> getAllForRecommendation(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            Pageable pageable) {
        verifyApiKey(apiKey);

        Page<ProductCatalogSyncResponse> page = productService.getActiveProductsForInternalSync(pageable)
                .map(ProductCatalogSyncResponse::from);
        return ApiResponse.ok(PageResponse.of(page));
    }

    private void verifyApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank() || !internalApiKey.equals(apiKey)) {
            throw new ForbiddenException("Invalid internal API key");
        }
    }
}
