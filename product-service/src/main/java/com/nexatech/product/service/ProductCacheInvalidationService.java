package com.nexatech.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheInvalidationService {

    private final CacheManager cacheManager;

    public void evictProductCaches(String productId, String reason) {
        boolean inventoryDriven = reason != null
                && (reason.startsWith("INVENTORY_") || "LOW_STOCK".equals(reason));

        evictAll("productPages");
        evictAll("productStockBatch");

        if (StringUtils.hasText(productId)) {
            if (inventoryDriven) {
                // Product detail can be cached by both id and slug. Inventory events only
                // carry productId, so clear detail cache to avoid stale stock on PDP.
                evictAll("products");
            } else {
                evict("products", productId);
            }
            evict("productStock", productId);
        } else {
            evictAll("products");
            evictAll("productStock");
        }

        log.info("Product cache invalidated: productId={} reason={}", productId, reason);
    }

    private void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evictIfPresent(key);
        }
    }

    private void evictAll(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
