package com.nexatech.order.client;

import com.nexatech.order.client.dto.ProductInfo;
import com.nexatech.order.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client giao tiếp với product-service qua Eureka.
 */
@FeignClient(name = "product-service", path = "/api/v1/products", fallback = ProductClientFallback.class)
public interface ProductClient {

    @GetMapping("/{id}")
    ApiResponse<ProductInfo> getProduct(@PathVariable String id);
}
