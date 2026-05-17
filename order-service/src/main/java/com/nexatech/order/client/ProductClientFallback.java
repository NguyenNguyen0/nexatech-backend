package com.nexatech.order.client;

import com.nexatech.order.client.dto.ProductInfo;
import com.nexatech.order.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductClientFallback implements ProductClient {

    @Override
    public ApiResponse<ProductInfo> getProduct(String id) {
        log.warn("Fallback: product-service getProduct không khả dụng, productId={}", id);
        // Trả về data=null để OrderService dùng unitPrice từ request thay vì giá cứng
        return ApiResponse.ok(null);
    }
}
