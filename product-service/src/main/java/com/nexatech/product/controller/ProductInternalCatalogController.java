package com.nexatech.product.controller;

import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.PageResponse;
import com.nexatech.product.dto.response.ProductMinimalResponse;
import com.nexatech.product.exception.ForbiddenException;
import com.nexatech.product.handler.product.ProductQueryHandler;
import com.nexatech.product.query.product.GetActiveProductsForSyncQuery;
import com.nexatech.product.query.product.LookupProductsByIdsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/products")
@RequiredArgsConstructor
public class ProductInternalCatalogController {

    private final ProductQueryHandler productQueryHandler;

    @Value("${product.internal.api-key:change-me}")
    private String internalApiKey;

    @GetMapping("/all")
    public ApiResponse<PageResponse<ProductMinimalResponse>> getAllForRecommendation(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            Pageable pageable) {
        verifyApiKey(apiKey);

        Page<ProductMinimalResponse> page = productQueryHandler.handle(new GetActiveProductsForSyncQuery(), pageable);
        return ApiResponse.ok(PageResponse.of(page));
    }

    @GetMapping("/lookup")
    public ApiResponse<List<ProductMinimalResponse>> lookupProductsForRecommendation(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            @RequestParam(name = "ids") List<String> ids) {
        verifyApiKey(apiKey);
        return ApiResponse.ok(productQueryHandler.handle(new LookupProductsByIdsQuery(ids)));
    }

    private void verifyApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank() || !internalApiKey.equals(apiKey)) {
            throw new ForbiddenException("Invalid internal API key");
        }
    }
}
