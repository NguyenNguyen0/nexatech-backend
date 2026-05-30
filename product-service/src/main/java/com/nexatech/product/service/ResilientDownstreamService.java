package com.nexatech.product.service;

import com.nexatech.product.client.InventoryClient;
import com.nexatech.product.client.OrderClient;
import com.nexatech.product.client.StorageClient;
import com.nexatech.product.client.dto.InventoryStockResponse;
import com.nexatech.product.dto.response.StorageUploadResult;
import com.nexatech.product.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResilientDownstreamService {

    private final StorageClient storageClient;
    private final OrderClient orderClient;
    private final InventoryClient inventoryClient;

    @CircuitBreaker(name = "storageService", fallbackMethod = "uploadToStorageFallback")
    @Retry(name = "storageService")
    public StorageUploadResult uploadToStorage(MultipartFile file, String bucket) {
        return storageClient.upload(file, bucket);
    }

    @CircuitBreaker(name = "storageService", fallbackMethod = "deleteFromStorageFallback")
    @Retry(name = "storageService")
    public void deleteFromStorage(String bucket, String key) {
        storageClient.delete(bucket, key);
    }

    @CircuitBreaker(name = "orderService", fallbackMethod = "checkPurchasedFallback")
    @Retry(name = "orderService")
    public Boolean checkPurchased(UUID userId, String productId) {
        return orderClient.checkPurchased(userId, productId).getData();
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getStockFallback")
    @Retry(name = "inventoryService")
    @Cacheable(value = "productStock", key = "#productId", unless = "#result == null || #result.data == null")
    public InventoryStockResponse getStock(String productId) {
        return inventoryClient.getStock(productId);
    }

    @SuppressWarnings("unused")
    private StorageUploadResult uploadToStorageFallback(MultipartFile file, String bucket, Throwable throwable) {
        throw new ServiceUnavailableException(
                "Storage service is temporarily unavailable. Please retry image upload later.",
                throwable);
    }

    @SuppressWarnings("unused")
    private void deleteFromStorageFallback(String bucket, String key, Throwable throwable) {
        throw new ServiceUnavailableException(
                "Storage service is temporarily unavailable. Please retry image deletion later.",
                throwable);
    }

    @SuppressWarnings("unused")
    private Boolean checkPurchasedFallback(UUID userId, String productId, Throwable throwable) {
        throw new ServiceUnavailableException(
                "Order service is temporarily unavailable. Cannot verify purchase right now.",
                throwable);
    }

    @SuppressWarnings("unused")
    private InventoryStockResponse getStockFallback(String productId, Throwable throwable) {
        log.warn("Inventory service unavailable for product {}: {}", productId, throwable.getMessage());
        return null;
    }
}
