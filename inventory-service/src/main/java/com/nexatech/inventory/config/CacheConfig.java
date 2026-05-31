package com.nexatech.inventory.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactory;

    public CacheConfig(ObjectProvider<RedisConnectionFactory> redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Bean
    @Override
    public RedisCacheManager cacheManager() {
        RedisConnectionFactory factory = redisConnectionFactory.getObject();
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING);

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(mapper)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .withCacheConfiguration("inventoryLists", defaults.entryTtl(Duration.ofMinutes(3)))
                .withCacheConfiguration("inventoryStock", defaults.entryTtl(Duration.ofMinutes(3)))
                .withCacheConfiguration("inventoryStockBatch", defaults.entryTtl(Duration.ofMinutes(3)))
                .withCacheConfiguration("inventoryMovements", defaults.entryTtl(Duration.ofMinutes(2)))
                .withCacheConfiguration("lowStockInventory", defaults.entryTtl(Duration.ofMinutes(3)))
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                logCacheError("get", exception, cache, key);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                logCacheError("put", exception, cache, key);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                logCacheError("evict", exception, cache, key);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                logCacheError("clear", exception, cache, null);
            }
        };
    }

    private static void logCacheError(String operation, RuntimeException exception, Cache cache, Object key) {
        log.warn("Ignoring Redis cache {} error for cache='{}', key='{}': {}",
                operation, cache.getName(), key, exception.getMessage());
    }
}
