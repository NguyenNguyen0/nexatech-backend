package com.nexatech.product.client;

import com.nexatech.product.client.dto.OrderApiResponse;
import com.nexatech.product.client.dto.OrderPurchasedResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "order-service")
public interface OrderClient {

    @GetMapping("/api/v1/orders/check-purchased")
    OrderPurchasedResponse checkPurchased(
            @RequestParam UUID userId,
            @RequestParam String productId);

    @GetMapping("/api/v1/orders/product-purchase-count")
    OrderApiResponse<Integer> getProductPurchaseCount(
            @RequestParam UUID userId,
            @RequestParam String productId);
}
