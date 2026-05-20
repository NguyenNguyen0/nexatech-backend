package com.nexatech.product.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.nexatech.product.dto.response.IdReferenceIntegrityResponse;
import com.nexatech.product.util.MongoIdUtils;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DataIntegrityService {

    private static final int SAMPLE_LIMIT = 20;

    private final MongoTemplate mongoTemplate;

    public IdReferenceIntegrityResponse checkIdReferences() {
        List<Document> activeCategories = findActiveDocuments("categories", "_id", "parentId");
        List<Document> activeBrands = findActiveDocuments("brands", "_id", "parentBrandId");
        List<Document> activeProducts = findActiveDocuments("products", "_id", "primaryCategoryId", "brandId", "categoryIds");
        List<Document> reviews = findDocuments("reviews", "_id", "productId");

        Set<String> activeCategoryIds = extractIdSet(activeCategories);
        Set<String> activeBrandIds = extractIdSet(activeBrands);
        Set<String> activeProductIds = extractIdSet(activeProducts);

        long orphanCategoryParentRefs = 0;
        long orphanBrandParentRefs = 0;
        long orphanProductPrimaryCategoryRefs = 0;
        long orphanProductBrandRefs = 0;
        long orphanProductCategoryArrayRefs = 0;
        long orphanReviewProductRefs = 0;

        List<String> orphanCategoryParentSamples = new ArrayList<>();
        List<String> orphanBrandParentSamples = new ArrayList<>();
        List<String> orphanProductPrimaryCategorySamples = new ArrayList<>();
        List<String> orphanProductBrandSamples = new ArrayList<>();
        List<String> orphanProductCategoryArraySamples = new ArrayList<>();
        List<String> orphanReviewProductSamples = new ArrayList<>();

        for (Document category : activeCategories) {
            String currentId = normalizeId(category.get("_id"));
            String parentId = normalizeId(category.get("parentId"));
            if (StringUtils.hasText(parentId) && !activeCategoryIds.contains(parentId)) {
                orphanCategoryParentRefs++;
                addSample(orphanCategoryParentSamples, currentId + " -> parent:" + parentId);
            }
        }

        for (Document brand : activeBrands) {
            String currentId = normalizeId(brand.get("_id"));
            String parentId = normalizeId(brand.get("parentBrandId"));
            if (StringUtils.hasText(parentId) && !activeBrandIds.contains(parentId)) {
                orphanBrandParentRefs++;
                addSample(orphanBrandParentSamples, currentId + " -> parent:" + parentId);
            }
        }

        for (Document product : activeProducts) {
            String productId = normalizeId(product.get("_id"));

            String primaryCategoryId = normalizeId(product.get("primaryCategoryId"));
            if (StringUtils.hasText(primaryCategoryId) && !activeCategoryIds.contains(primaryCategoryId)) {
                orphanProductPrimaryCategoryRefs++;
                addSample(orphanProductPrimaryCategorySamples, productId + " -> primaryCategoryId:" + primaryCategoryId);
            }

            String brandId = normalizeId(product.get("brandId"));
            if (StringUtils.hasText(brandId) && !activeBrandIds.contains(brandId)) {
                orphanProductBrandRefs++;
                addSample(orphanProductBrandSamples, productId + " -> brandId:" + brandId);
            }

            Object categoryIdsObject = product.get("categoryIds");
            if (categoryIdsObject instanceof List<?> categoryIds) {
                for (Object categoryIdObject : categoryIds) {
                    String categoryId = normalizeId(categoryIdObject);
                    if (!StringUtils.hasText(categoryId)) {
                        continue;
                    }
                    if (!activeCategoryIds.contains(categoryId)) {
                        orphanProductCategoryArrayRefs++;
                        addSample(orphanProductCategoryArraySamples, productId + " -> categoryIds[]:" + categoryId);
                    }
                }
            }
        }

        for (Document review : reviews) {
            String reviewId = normalizeId(review.get("_id"));
            String productId = normalizeId(review.get("productId"));
            if (StringUtils.hasText(productId) && !activeProductIds.contains(productId)) {
                orphanReviewProductRefs++;
                addSample(orphanReviewProductSamples, reviewId + " -> productId:" + productId);
            }
        }

        boolean healthy = orphanCategoryParentRefs == 0
                && orphanBrandParentRefs == 0
                && orphanProductPrimaryCategoryRefs == 0
                && orphanProductBrandRefs == 0
                && orphanProductCategoryArrayRefs == 0
                && orphanReviewProductRefs == 0;

        return IdReferenceIntegrityResponse.builder()
                .healthy(healthy)
                .activeCategoryCount(activeCategories.size())
                .activeBrandCount(activeBrands.size())
                .activeProductCount(activeProducts.size())
                .reviewCount(reviews.size())
                .orphanCategoryParentRefs(orphanCategoryParentRefs)
                .orphanBrandParentRefs(orphanBrandParentRefs)
                .orphanProductPrimaryCategoryRefs(orphanProductPrimaryCategoryRefs)
                .orphanProductBrandRefs(orphanProductBrandRefs)
                .orphanProductCategoryArrayRefs(orphanProductCategoryArrayRefs)
                .orphanReviewProductRefs(orphanReviewProductRefs)
                .orphanCategoryParentSamples(orphanCategoryParentSamples)
                .orphanBrandParentSamples(orphanBrandParentSamples)
                .orphanProductPrimaryCategorySamples(orphanProductPrimaryCategorySamples)
                .orphanProductBrandSamples(orphanProductBrandSamples)
                .orphanProductCategoryArraySamples(orphanProductCategoryArraySamples)
                .orphanReviewProductSamples(orphanReviewProductSamples)
                .build();
    }

    private List<Document> findActiveDocuments(String collectionName, String... fields) {
        MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);
        return collection.find(Filters.eq("active", true))
                .projection(Projections.include(fields))
                .into(new ArrayList<>());
    }

    private List<Document> findDocuments(String collectionName, String... fields) {
        MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);
        return collection.find()
                .projection(Projections.include(fields))
                .into(new ArrayList<>());
    }

    private Set<String> extractIdSet(List<Document> documents) {
        Set<String> ids = new HashSet<>();
        for (Document document : documents) {
            String id = normalizeId(document.get("_id"));
            if (StringUtils.hasText(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private String normalizeId(Object value) {
        return MongoIdUtils.normalize(value);
    }

    private void addSample(List<String> samples, String sample) {
        if (samples.size() < SAMPLE_LIMIT) {
            samples.add(sample);
        }
    }
}
