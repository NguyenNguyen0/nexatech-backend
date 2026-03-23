package com.nexatech.product.service;

import com.nexatech.product.dto.ProductRequest;
import com.nexatech.product.dto.ProductResponse;
import com.nexatech.product.event.ProductCatalogEvent;
import com.nexatech.product.event.ProductEventProducer;
import com.nexatech.product.exception.BadRequestException;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ProductMapper;
import com.nexatech.product.model.Product;
import com.nexatech.product.repository.CategoryRepository;
import com.nexatech.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ProductEventProducer productEventProducer;
    private final MongoTemplate mongoTemplate;

    public ProductResponse createProduct(ProductRequest request) {
        validateCategory(request.getCategoryId());

        Product product = productMapper.toEntity(request);
        product.setName(normalizeName(request.getName()));
        product.setSlug(generateUniqueSlug(request.getName(), null));
        product.setActive(true);

        Product saved = productRepository.save(product);
        publishEvent(saved, "PRODUCT_CREATED");
        return productMapper.toResponse(saved);
    }

    public ProductResponse updateProduct(String id, ProductRequest request) {
        Product product = getActiveProduct(id);
        validateCategory(request.getCategoryId());

        String oldName = product.getName();
        productMapper.updateEntity(request, product);
        product.setName(normalizeName(request.getName()));

        if (!oldName.equalsIgnoreCase(request.getName())) {
            product.setSlug(generateUniqueSlug(request.getName(), id));
        }

        Product saved = productRepository.save(product);
        publishEvent(saved, "PRODUCT_UPDATED");
        return productMapper.toResponse(saved);
    }

    public ProductResponse getProduct(String id) {
        return productMapper.toResponse(getActiveProduct(id));
    }

    public Page<ProductResponse> getProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable).map(productMapper::toResponse);
    }

    public Page<ProductResponse> searchProducts(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return getProducts(pageable);
        }

        String normalizedKeyword = keyword.trim();
        TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matching(normalizedKeyword);

        Query query = TextQuery.queryText(textCriteria)
                .sortByScore()
                .with(pageable)
                .addCriteria(Criteria.where("active").is(true));

        List<ProductResponse> content = mongoTemplate.find(query, Product.class).stream()
                .map(productMapper::toResponse)
                .toList();

        if (!content.isEmpty()) {
            long total = mongoTemplate.count(
                    TextQuery.queryText(textCriteria).addCriteria(Criteria.where("active").is(true)),
                    Product.class);
            return new PageImpl<>(content, pageable, total);
        }

        return productRepository.findByNameContainingIgnoreCaseAndActiveTrue(normalizedKeyword, pageable)
                .map(productMapper::toResponse);
    }

    public Page<ProductResponse> getProductsByCategory(String categoryId, Pageable pageable) {
        validateCategory(categoryId);
        return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable)
                .map(productMapper::toResponse);
    }

    public void deleteProduct(String id) {
        Product product = getActiveProduct(id);
        product.setActive(false);
        Product saved = productRepository.save(product);
        publishEvent(saved, "PRODUCT_DELETED");
    }

    private Product getActiveProduct(String id) {
        return productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    private void validateCategory(String categoryId) {
        if (!StringUtils.hasText(categoryId)) {
            return;
        }
        categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private String generateUniqueSlug(String name, String currentProductId) {
        String base = toSlug(name);
        String candidate = base;
        int suffix = 1;

        while (true) {
            Product existing = productRepository.findBySlug(candidate).orElse(null);
            if (existing == null || (currentProductId != null && currentProductId.equals(existing.getId()))) {
                return candidate;
            }
            candidate = base + "-" + suffix++;
        }
    }

    private String toSlug(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        String slug = normalized
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        return StringUtils.hasText(slug) ? slug : "product";
    }

    private void publishEvent(Product product, String eventType) {
        ProductCatalogEvent event = ProductCatalogEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .timestamp(Instant.now())
                .productId(product.getId())
                .name(product.getName())
                .categoryId(product.getCategoryId())
                .active(product.isActive())
                .build();
        productEventProducer.publishProductEvent(product.getId(), event);
    }
}
