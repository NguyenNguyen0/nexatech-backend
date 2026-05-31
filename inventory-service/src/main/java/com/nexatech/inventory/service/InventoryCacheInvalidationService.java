package com.nexatech.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCacheInvalidationService {

    private final CacheManager cacheManager;

    public void evictForProduct(String productId, String reason) {
        evictAll("inventoryLists");
        evictAll("lowStockInventory");
        evictAll("inventoryMovements");
        evictAll("inventoryStockBatch");

        if (StringUtils.hasText(productId)) {
            evict("inventoryStock", productId);
        } else {
            evictAll("inventoryStock");
        }

        log.info("Inventory cache invalidated: productId={} reason={}", productId, reason);
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
