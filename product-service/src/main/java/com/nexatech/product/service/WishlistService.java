package com.nexatech.product.service;

import com.nexatech.product.dto.response.ProductMinimalResponse;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ProductMapper;
import com.nexatech.product.model.Product;
import com.nexatech.product.model.Wishlist;
import com.nexatech.product.repository.WishlistRepository;
import com.nexatech.product.util.MongoIdUtils;
import com.nexatech.product.util.ProductQueryUtils;
import com.nexatech.product.util.TextUtils;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt");

    private final WishlistRepository wishlistRepository;
    private final ProductMapper productMapper;
    private final ProductResponseEnricher productResponseEnricher;
    private final MongoTemplate mongoTemplate;

    public boolean addToWishlist(UUID userId, String productId) {
        String normalizedUserId = userId.toString();
        Product product = findActiveProductById(productId);
        String normalizedProductId = MongoIdUtils.normalize(product.getId());

        if (wishlistRepository.findByUserIdAndProductId(normalizedUserId, normalizedProductId).isPresent()) {
            return true;
        }

        try {
            wishlistRepository.save(Wishlist.builder()
                    .userId(normalizedUserId)
                    .productId(normalizedProductId)
                    .build());
            return true;
        } catch (DuplicateKeyException ex) {
            return true;
        }
    }

    public boolean removeFromWishlist(UUID userId, String productId) {
        String normalizedUserId = userId.toString();
        String normalizedProductId = MongoIdUtils.normalize(productId);
        wishlistRepository.deleteByUserIdAndProductId(normalizedUserId, normalizedProductId == null ? productId : normalizedProductId);
        return false;
    }

    public Page<ProductMinimalResponse> getWishlist(UUID userId, Pageable pageable) {
        String normalizedUserId = userId.toString();
        Pageable safePageable = ProductQueryUtils.sanitizePageable(
                pageable,
                Sort.by(Sort.Direction.DESC, "createdAt"),
                ALLOWED_SORT_FIELDS);

        Page<Wishlist> wishlistPage = wishlistRepository.findByUserId(normalizedUserId, safePageable);
        List<String> pagedProductIds = wishlistPage.getContent().stream()
                .map(Wishlist::getProductId)
                .filter(java.util.Objects::nonNull)
                .toList();

        long total = countActiveWishlistProducts(normalizedUserId);
        if (total == 0) {
            return new PageImpl<>(List.of(), safePageable, 0);
        }

        if (pagedProductIds.isEmpty()) {
            return new PageImpl<>(List.of(), safePageable, total);
        }

        List<Product> products = findActiveProductsByIds(pagedProductIds);
        Map<String, Product> productById = new LinkedHashMap<>();
        for (Product product : products) {
            String normalized = MongoIdUtils.normalize(product.getId());
            if (normalized != null) {
                productById.put(normalized, product);
            }
        }

        List<ProductMinimalResponse> content = new ArrayList<>();
        for (String productId : pagedProductIds) {
            String normalized = MongoIdUtils.normalize(productId);
            Product product = normalized == null ? null : productById.get(normalized);
            if (product == null) {
                continue;
            }
            content.add(toMinimalCatalogResponse(product));
        }

        return new PageImpl<>(content, safePageable, total);
    }

    private long countActiveWishlistProducts(String userId) {
        Query query = Query.query(Criteria.where("userId").is(userId));
        query.fields().include("productId");

        List<String> productIds = mongoTemplate.find(query, Wishlist.class).stream()
                .map(Wishlist::getProductId)
                .filter(java.util.Objects::nonNull)
                .toList();

        if (productIds.isEmpty()) {
            return 0;
        }

        return countActiveProductsByIds(productIds);
    }

    private ProductMinimalResponse toMinimalCatalogResponse(Product product) {
        ProductMinimalResponse response = productMapper.toMinimalResponse(product);
        if (response.getOriginalPrice() == null) {
            response.setOriginalPrice(response.getCompareAtPrice());
        }
        return productResponseEnricher.enrichMinimalCatalogResponse(response);
    }

    private Product findActiveProductById(String productId) {
        String normalized = MongoIdUtils.normalize(productId);
        if (normalized == null) {
            throw new ResourceNotFoundException("Product", "id", productId);
        }

        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(normalized),
                Criteria.where("active").is(true)
        ));
        Product product = mongoTemplate.findOne(query, Product.class);
        if (product == null) {
            throw new ResourceNotFoundException("Product", "id", productId);
        }
        return product;
    }

    private List<Product> findActiveProductsByIds(List<String> ids) {
        Query query = buildActiveProductsQuery(ids);
        if (query == null) {
            return List.of();
        }
        return mongoTemplate.find(query, Product.class);
    }

    private long countActiveProductsByIds(List<String> ids) {
        Query query = buildActiveProductsQuery(ids);
        if (query == null) {
            return 0;
        }
        return mongoTemplate.count(query, Product.class);
    }

    private Query buildActiveProductsQuery(List<String> ids) {
        List<String> normalizedIds = TextUtils.normalizeDistinct(ids);
        if (normalizedIds.isEmpty()) {
            return null;
        }
        return Query.query(new Criteria().andOperator(
                Criteria.where("active").is(true),
                Criteria.where("_id").in(normalizedIds)
        ));
    }
}
