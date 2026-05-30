package com.nexatech.product.handler.product;

import com.nexatech.product.command.product.CreateProductCommand;
import com.nexatech.product.command.product.DeleteProductCommand;
import com.nexatech.product.command.product.RestoreProductCommand;
import com.nexatech.product.command.product.UpdateProductCommand;
import com.nexatech.product.dto.response.ProductResponse;
import com.nexatech.product.event.ProductCatalogEvent;
import com.nexatech.product.event.ProductEventProducer;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ProductMapper;
import com.nexatech.product.model.Brand;
import com.nexatech.product.model.Category;
import com.nexatech.product.model.Product;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.service.ProductResponseEnricher;
import com.nexatech.product.util.MongoIdUtils;
import com.nexatech.product.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductCommandHandler {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductEventProducer productEventProducer;
    private final ProductResponseEnricher productResponseEnricher;
    private final MongoTemplate mongoTemplate;

    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productPages", allEntries = true)
    })
    public ProductResponse handle(CreateProductCommand command) {
        validateCategory(command.primaryCategoryId());
        validateCategoryIds(command.categoryIds());
        validateBrand(command.brandId());

        Product product = new Product();
        applyCommand(product, command);
        String normalizedName = TextUtils.normalizeName(command.name());
        product.setName(normalizedName);
        product.setSlug(generateUniqueSlug(normalizedName, null));
        product.setImages(command.images() == null ? null : new ArrayList<>(command.images()));
        product.setThumbnail(resolveThumbnail(command.thumbnail(), product.getImages()));
        product.setActive(true);

        Product saved = productRepository.save(product);
        publishEvent(saved, "PRODUCT_CREATED");
        return productResponseEnricher.enrichCatalogResponse(productMapper.toResponse(saved));
    }

    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productPages", allEntries = true)
    })
    public ProductResponse handle(UpdateProductCommand command) {
        Product product = getActiveProduct(command.id());
        validateCategory(command.primaryCategoryId());
        validateCategoryIds(command.categoryIds());
        validateBrand(command.brandId());

        String oldName = product.getName();
        applyCommand(product, command);
        String normalizedName = TextUtils.normalizeName(command.name());
        product.setName(normalizedName);
        product.setImages(command.images() == null ? null : new ArrayList<>(command.images()));
        product.setThumbnail(resolveThumbnail(command.thumbnail(), product.getImages()));

        if (oldName == null || !oldName.equalsIgnoreCase(normalizedName)) {
            product.setSlug(generateUniqueSlug(normalizedName, command.id()));
        }

        Product saved = productRepository.save(product);
        publishEvent(saved, "PRODUCT_UPDATED");
        return productResponseEnricher.enrichCatalogResponse(productMapper.toResponse(saved));
    }

    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productPages", allEntries = true)
    })
    public void handle(DeleteProductCommand command) {
        Product product = getActiveProduct(command.id());
        product.setActive(false);
        Product saved = productRepository.save(product);
        publishEvent(saved, "PRODUCT_DELETED");
    }

    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productPages", allEntries = true)
    })
    public ProductResponse handle(RestoreProductCommand command) {
        Product product = getProductById(command.id());
        if (product.isActive()) {
            return productResponseEnricher.enrichCatalogResponse(productMapper.toResponse(product));
        }

        validateCategory(product.getPrimaryCategoryId());
        validateCategoryIds(product.getCategoryIds());
        validateBrand(product.getBrandId());

        product.setActive(true);
        Product saved = productRepository.save(product);
        publishEvent(saved, "PRODUCT_RESTORED");
        return productResponseEnricher.enrichCatalogResponse(productMapper.toResponse(saved));
    }

    // ---------- private helpers ----------

    private void applyCommand(Product product, CreateProductCommand cmd) {
        product.setDescription(cmd.description());
        product.setDescriptionHtml(cmd.descriptionHtml());
        product.setPrice(cmd.price());
        product.setCompareAtPrice(cmd.compareAtPrice());
        product.setSku(cmd.sku());
        product.setPrimaryCategoryId(cmd.primaryCategoryId());
        product.setPrimaryCategorySlug(cmd.primaryCategorySlug());
        product.setCategoryIds(cmd.categoryIds());
        product.setCategorySlugs(cmd.categorySlugs());
        product.setBrandId(cmd.brandId());
        product.setSpecs(cmd.specs());
        product.setTags(cmd.tags());
        product.setMetadata(cmd.metadata());
    }

    private void applyCommand(Product product, UpdateProductCommand cmd) {
        product.setDescription(cmd.description());
        product.setDescriptionHtml(cmd.descriptionHtml());
        product.setPrice(cmd.price());
        product.setCompareAtPrice(cmd.compareAtPrice());
        product.setSku(cmd.sku());
        product.setPrimaryCategoryId(cmd.primaryCategoryId());
        product.setPrimaryCategorySlug(cmd.primaryCategorySlug());
        product.setCategoryIds(cmd.categoryIds());
        product.setCategorySlugs(cmd.categorySlugs());
        product.setBrandId(cmd.brandId());
        product.setSpecs(cmd.specs());
        product.setTags(cmd.tags());
        product.setMetadata(cmd.metadata());
    }

    private Product getActiveProduct(String id) {
        return productRepository.findById(id)
            .filter(Product::isActive)
            .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    private Product getProductById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    private void validateCategory(String categoryId) {
        if (!StringUtils.hasText(categoryId)) {
            return;
        }
        Query query = Query.query(new Criteria().andOperator(
                MongoIdUtils.idEqualsCriteria("_id", categoryId),
                Criteria.where("active").is(true)
        ));
        if (mongoTemplate.findOne(query, Category.class) == null) {
            throw new ResourceNotFoundException("Category", "id", categoryId);
        }
    }

    private void validateCategoryIds(List<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        for (String id : categoryIds) {
            if (StringUtils.hasText(id)) {
                validateCategory(id);
            }
        }
    }

    private void validateBrand(String brandId) {
        if (!StringUtils.hasText(brandId)) {
            return;
        }
        Query query = Query.query(new Criteria().andOperator(
                MongoIdUtils.idEqualsCriteria("_id", brandId),
                Criteria.where("active").is(true)
        ));
        if (mongoTemplate.findOne(query, Brand.class) == null) {
            throw new ResourceNotFoundException("Brand", "id", brandId);
        }
    }

    private String generateUniqueSlug(String name, String currentProductId) {
        String base = TextUtils.toSlug(name, "product");
        String candidate = base;
        int suffix = 1;
        while (true) {
            Product existing = productRepository.findBySlug(candidate).orElse(null);
            if (existing == null || MongoIdUtils.equalsId(currentProductId, existing.getId())) {
                return candidate;
            }
            candidate = base + "-" + suffix++;
        }
    }

    private String resolveThumbnail(String requestedThumbnail, List<String> images) {
        if (StringUtils.hasText(requestedThumbnail)) {
            return requestedThumbnail;
        }
        if (images != null && !images.isEmpty()) {
            return images.getFirst();
        }
        return null;
    }

    private void publishEvent(Product product, String eventType) {
        ProductCatalogEvent event = ProductCatalogEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .timestamp(Instant.now())
                .productId(product.getId())
                .name(product.getName())
                .primaryCategoryId(product.getPrimaryCategoryId())
                .active(product.isActive())
                .build();
        productEventProducer.publishProductEvent(product.getId(), event);
    }
}
