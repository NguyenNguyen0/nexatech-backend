package com.nexatech.product.service;

import com.nexatech.product.dto.ProductRequest;
import com.nexatech.product.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    public ProductResponse createProduct(ProductRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public ProductResponse updateProduct(String id, ProductRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public ProductResponse getProduct(String id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Page<ProductResponse> getProducts(Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Page<ProductResponse> searchProducts(String keyword, Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Page<ProductResponse> getProductsByCategory(String categoryId, Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void deleteProduct(String id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
