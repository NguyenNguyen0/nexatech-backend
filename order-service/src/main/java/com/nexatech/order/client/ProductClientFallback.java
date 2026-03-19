package com.nexatech.order.client;

import com.nexatech.order.client.dto.ProductInfo;
import com.nexatech.order.dto.response.ApiResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ProductClientFallback implements ProductClient {

    @Override
    public ApiResponse<ProductInfo> getProduct(String id) {
        ProductInfo stub = new ProductInfo();
        stub.setId(id);
        stub.setName("Product " + id);
        stub.setPrice(new BigDecimal("10.00"));
        stub.setActive(true);
        return ApiResponse.ok(stub);
    }
}
