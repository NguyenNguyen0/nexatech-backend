package com.nexatech.product.service;

import com.nexatech.product.dto.request.ProductRequest;
import com.nexatech.product.dto.response.ProductResponse;
import com.nexatech.product.event.ProductCatalogEvent;
import com.nexatech.product.event.ProductEventProducer;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ProductMapper;
import com.nexatech.product.model.Brand;
import com.nexatech.product.model.Product;
import com.nexatech.product.model.Review;
import com.nexatech.product.repository.BrandRepository;
import com.nexatech.product.repository.CategoryRepository;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
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
    private final BrandRepository brandRepository;
    private final ReviewRepository reviewRepository;
    private final ProductMapper productMapper;
    private final ProductEventProducer productEventProducer;
    private final MongoTemplate mongoTemplate;

    public ProductResponse createProduct(ProductRequest request) {
        validateCategory(request.getCategoryId());
        validateBrand(request.getBrandId());

        Product product = productMapper.toEntity(request);
        product.setName(normalizeName(request.getName()));
        product.setSlug(generateUniqueSlug(request.getName(), null));
        product.setActive(true);

        Product saved = productRepository.save(product);
        publishEvent(saved, "PRODUCT_CREATED");
        return enrichWithBrandName(productMapper.toResponse(saved));
    }

    public ProductResponse updateProduct(String id, ProductRequest request) {
        Product product = getActiveProduct(id);
        validateCategory(request.getCategoryId());
        validateBrand(request.getBrandId());

        String oldName = product.getName();
        productMapper.updateEntity(request, product);
        product.setName(normalizeName(request.getName()));

        if (!oldName.equalsIgnoreCase(request.getName())) {
            product.setSlug(generateUniqueSlug(request.getName(), id));
        }

        Product saved = productRepository.save(product);
        publishEvent(saved, "PRODUCT_UPDATED");
        return enrichWithBrandName(productMapper.toResponse(saved));
    }

    public ProductResponse getProduct(String id) {
        return enrichWithBrandName(productMapper.toResponse(getActiveProduct(id)));
    }

    public Page<ProductResponse> getProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable).map(p -> enrichWithBrandName(productMapper.toResponse(p)));
    }

    public Page<ProductResponse> searchProducts(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return getProducts(pageable);
        }

        String normalizedKeyword = keyword.trim();
        try {
            TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matching(normalizedKeyword);

            Query query = TextQuery.queryText(textCriteria)
                    .sortByScore()
                    .with(pageable)
                    .addCriteria(Criteria.where("active").is(true));

            List<ProductResponse> content = mongoTemplate.find(query, Product.class).stream()
                    .map(p -> enrichWithBrandName(productMapper.toResponse(p)))
                    .toList();

            if (!content.isEmpty()) {
                long total = mongoTemplate.count(
                        TextQuery.queryText(textCriteria).addCriteria(Criteria.where("active").is(true)),
                        Product.class);
                return new PageImpl<>(content, pageable, total);
            }
        } catch (UncategorizedMongoDbException ex) {
            // Fallback to regex search when text index is missing.
        }

        return productRepository.findByNameContainingIgnoreCaseAndActiveTrue(normalizedKeyword, pageable)
                .map(p -> enrichWithBrandName(productMapper.toResponse(p)));
    }

    public Page<ProductResponse> getProductsByCategory(String categoryId, Pageable pageable) {
        validateCategory(categoryId);

        Query query = Query.query(new Criteria().andOperator(
                        Criteria.where("active").is(true),
                        categoryIdCriteria("categoryId", categoryId)))
                .with(pageable);

        List<ProductResponse> content = mongoTemplate.find(query, Product.class).stream()
                .map(p -> enrichWithBrandName(productMapper.toResponse(p)))
                .toList();
        long total = mongoTemplate.count(
                Query.query(new Criteria().andOperator(
                        Criteria.where("active").is(true),
                        categoryIdCriteria("categoryId", categoryId))),
                Product.class);

        return new PageImpl<>(content, pageable, total);
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

    private void validateBrand(String brandId) {
        if (!StringUtils.hasText(brandId)) {
            return;
        }
        brandRepository.findByIdAndActiveTrue(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", "id", brandId));
    }

    private ProductResponse enrichWithBrandName(ProductResponse response) {
        if (StringUtils.hasText(response.getBrandId())) {
            response.setBrandName(
                    brandRepository.findById(response.getBrandId())
                            .map(Brand::getName)
                            .orElse(null));
        }
        double avg = reviewRepository.findByProductIdAndApprovedTrue(response.getId())
                .stream().mapToInt(Review::getRating).average().orElse(0.0);
        response.setAverageRating(avg > 0 ? Math.round(avg * 10.0) / 10.0 : null);
        return response;
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

    private Criteria categoryIdCriteria(String fieldName, String idValue) {
        if (ObjectId.isValid(idValue)) {
            return Criteria.where(fieldName).in(idValue, new ObjectId(idValue));
        }
        return Criteria.where(fieldName).is(idValue);
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
