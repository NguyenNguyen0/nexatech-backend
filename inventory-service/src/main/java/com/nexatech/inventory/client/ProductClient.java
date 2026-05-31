package com.nexatech.inventory.client;

import com.nexatech.inventory.client.dto.ProductSummary;
import com.nexatech.inventory.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "product-service", url = "${product-service.url:}", path = "/api/v1/internal/products")
public interface ProductClient {

    @GetMapping("/lookup")
    ApiResponse<List<ProductSummary>> lookupProducts(
            @RequestHeader("X-Internal-Api-Key") String apiKey,
            @RequestParam("ids") List<String> ids);
}
