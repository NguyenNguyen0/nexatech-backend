package com.nexatech.inventory.client;

import com.nexatech.inventory.client.dto.ProductSummary;
import com.nexatech.inventory.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", url = "${product-service.url:}", path = "/api/v1/products")
public interface ProductClient {

    @GetMapping("/{id}")
    ApiResponse<ProductSummary> getProduct(@PathVariable String id);
}