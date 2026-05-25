package com.nexatech.product.service;

import com.nexatech.product.dto.response.ProductMinimalResponse;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ProductMapper;
import com.nexatech.product.model.Product;
import com.nexatech.product.model.Wishlist;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.repository.WishlistRepository;
import com.nexatech.product.util.ProductQueryUtils;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
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
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductResponseEnricher productResponseEnricher;
    private final MongoTemplate mongoTemplate;

    public boolean addToWishlist(UUID userId, String productId) {
        String normalizedUserId = userId.toString();
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        if (wishlistRepository.findByUserIdAndProductId(normalizedUserId, product.getId()).isPresent()) {
            return true;
        }

        try {
            wishlistRepository.save(Wishlist.builder()
                    .userId(normalizedUserId)
                    .productId(product.getId())
                    .build());
            return true;
        } catch (DuplicateKeyException ex) {
            return true;
        }
    }

    public boolean removeFromWishlist(UUID userId, String productId) {
        String normalizedUserId = userId.toString();
        wishlistRepository.deleteByUserIdAndProductId(normalizedUserId, productId);
        return false;
    }

    public Page<ProductMinimalResponse> getWishlist(UUID userId, Pageable pageable) {
        String normalizedUserId = userId.toString();
        Pageable safePageable = ProductQueryUtils.sanitizePageable(
                pageable,
                Sort.by(Sort.Direction.DESC, "createdAt"),
                ALLOWED_SORT_FIELDS);

        Sort.Direction createdAtDirection = resolveCreatedAtDirection(safePageable.getSort());
        long total = countActiveWishlistProducts(normalizedUserId);
        if (total == 0) {
            return new PageImpl<>(List.of(), safePageable, 0);
        }

        List<String> pagedProductIds = fetchPagedActiveProductIds(normalizedUserId, safePageable, createdAtDirection);
        if (pagedProductIds.isEmpty()) {
            return new PageImpl<>(List.of(), safePageable, total);
        }

        List<Product> products = productRepository.findByIdInAndActiveTrue(pagedProductIds);
        Map<String, Product> productById = new LinkedHashMap<>();
        for (Product product : products) {
            productById.put(product.getId(), product);
        }

        List<ProductMinimalResponse> content = new ArrayList<>();
        for (String productId : pagedProductIds) {
            Product product = productById.get(productId);
            if (product == null) {
                continue;
            }
            content.add(toMinimalCatalogResponse(product));
        }

        return new PageImpl<>(content, safePageable, total);
    }

    private List<String> fetchPagedActiveProductIds(String userId, Pageable pageable, Sort.Direction direction) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(org.springframework.data.mongodb.core.query.Criteria.where("userId").is(userId)),
                Aggregation.lookup("products", "productId", "_id", "product"),
                Aggregation.unwind("product"),
                Aggregation.match(org.springframework.data.mongodb.core.query.Criteria.where("product.active").is(true)),
                Aggregation.sort(direction, "createdAt"),
                Aggregation.skip(pageable.getOffset()),
                Aggregation.limit(pageable.getPageSize()),
                Aggregation.project("productId")
        );

        AggregationResults<Document> result = mongoTemplate.aggregate(aggregation, Wishlist.class, Document.class);
        List<String> productIds = new ArrayList<>();
        for (Document document : result.getMappedResults()) {
            String productId = document.getString("productId");
            if (productId != null) {
                productIds.add(productId);
            }
        }
        return productIds;
    }

    private long countActiveWishlistProducts(String userId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(org.springframework.data.mongodb.core.query.Criteria.where("userId").is(userId)),
                Aggregation.lookup("products", "productId", "_id", "product"),
                Aggregation.unwind("product"),
                Aggregation.match(org.springframework.data.mongodb.core.query.Criteria.where("product.active").is(true)),
                Aggregation.count().as("total")
        );

        AggregationResults<Document> result = mongoTemplate.aggregate(aggregation, Wishlist.class, Document.class);
        Document countDoc = result.getUniqueMappedResult();
        if (countDoc == null) {
            return 0;
        }
        Number total = countDoc.get("total", Number.class);
        return total == null ? 0 : total.longValue();
    }

    private Sort.Direction resolveCreatedAtDirection(Sort sort) {
        if (sort == null) {
            return Sort.Direction.DESC;
        }
        for (Sort.Order order : sort) {
            if ("createdAt".equals(order.getProperty())) {
                return order.getDirection();
            }
        }
        return Sort.Direction.DESC;
    }

    private ProductMinimalResponse toMinimalCatalogResponse(Product product) {
        ProductMinimalResponse response = productMapper.toMinimalResponse(product);
        if (response.getOriginalPrice() == null) {
            response.setOriginalPrice(response.getCompareAtPrice());
        }
        return productResponseEnricher.enrichMinimalCatalogResponse(response);
    }
}
