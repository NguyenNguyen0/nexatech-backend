package com.nexatech.product.service;

import com.nexatech.product.dto.request.ProductRequest;
import com.nexatech.product.dto.response.ProductResponse;
import com.nexatech.product.client.InventoryClient;
import com.nexatech.product.client.dto.InventoryStockResponse;
import com.nexatech.product.dto.response.ProductSuggestResponse;
import com.nexatech.product.event.ProductCatalogEvent;
import com.nexatech.product.event.ProductEventProducer;
import com.nexatech.product.exception.BadRequestException;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ProductMapper;
import com.nexatech.product.model.Brand;
import com.nexatech.product.model.Category;
import com.nexatech.product.model.Product;
import com.nexatech.product.repository.BrandRepository;
import com.nexatech.product.repository.CategoryRepository;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.repository.ReviewRepository;
import com.nexatech.product.util.MongoIdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("price", "name", "createdAt");
    private static final int RELATED_PRODUCT_LIMIT = 8;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ReviewRepository reviewRepository;
    private final ProductMapper productMapper;
    private final ProductEventProducer productEventProducer;
    private final MongoTemplate mongoTemplate;
    private final InventoryClient inventoryClient;

    public ProductResponse createProduct(ProductRequest request) {
        validateCategory(request.getPrimaryCategoryId());
        validateCategoryIds(request.getCategoryIds());
        validateBrand(request.getBrandId());

        Product product = productMapper.toEntity(request);
        String normalizedName = normalizeName(request.getName());
        product.setName(normalizedName);
        product.setSlug(generateUniqueSlug(normalizedName, null));
        product.setImages(request.getImages() == null ? null : new ArrayList<>(request.getImages()));
        product.setThumbnail(resolveThumbnail(request.getThumbnail(), product.getImages()));
        product.setActive(true);

        Product saved = productRepository.save(product);
        publishEvent(saved, "PRODUCT_CREATED");
        return enrichProductResponse(productMapper.toResponse(saved));
    }

    public ProductResponse updateProduct(String id, ProductRequest request) {
        Product product = getActiveProduct(id);
        validateCategory(request.getPrimaryCategoryId());
        validateCategoryIds(request.getCategoryIds());
        validateBrand(request.getBrandId());

        String oldName = product.getName();
        productMapper.updateEntity(request, product);
        String normalizedName = normalizeName(request.getName());
        product.setName(normalizedName);
        product.setImages(request.getImages() == null ? null : new ArrayList<>(request.getImages()));
        product.setThumbnail(resolveThumbnail(request.getThumbnail(), product.getImages()));

        if (oldName == null || !oldName.equalsIgnoreCase(normalizedName)) {
            product.setSlug(generateUniqueSlug(normalizedName, id));
        }

        Product saved = productRepository.save(product);
        publishEvent(saved, "PRODUCT_UPDATED");
        return enrichProductResponse(productMapper.toResponse(saved));
    }

    public ProductResponse getProduct(String id) {
        return enrichProductResponse(productMapper.toResponse(getActiveProduct(id)));
    }

    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", slug));
        return enrichProductResponse(productMapper.toResponse(product));
    }

    public Page<ProductResponse> getProducts(String categoryId,
                                             String categorySlug,
                                             String brandId,
                                             BigDecimal minPrice,
                                             BigDecimal maxPrice,
                                             Double minRating,
                                             Double maxRating,
                                             Boolean featured,
                                             Map<String, List<String>> specsFilters,
                                             Pageable pageable) {
        if (Boolean.TRUE.equals(featured)) {
            return getFeaturedProducts(pageable);
        }

        validatePriceRange(minPrice, maxPrice);
        Pageable safePageable = sanitizePageable(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("active").is(true));

        String resolvedCategoryId = resolveCategoryId(categoryId, categorySlug);
        if (StringUtils.hasText(resolvedCategoryId)) {
            criteriaList.add(categoryArrayCriteria(resolvedCategoryId));
        }
        if (StringUtils.hasText(brandId)) {
            validateBrand(brandId);
            criteriaList.add(idCriteria("brandId", brandId));
        }
        if (minPrice != null || maxPrice != null) {
            Criteria priceCriteria = Criteria.where("price");
            if (minPrice != null) {
                priceCriteria = priceCriteria.gte(minPrice);
            }
            if (maxPrice != null) {
                priceCriteria = priceCriteria.lte(maxPrice);
            }
            criteriaList.add(priceCriteria);
        }
        criteriaList.addAll(buildSpecsCriteria(specsFilters));

        Criteria finalCriteria = criteriaList.size() == 1
                ? criteriaList.getFirst()
                : new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));

        boolean hasRatingFilter = minRating != null || maxRating != null;
        if (!hasRatingFilter) {
            Query query = Query.query(finalCriteria).with(safePageable);
            List<ProductResponse> content = mongoTemplate.find(query, Product.class).stream()
                    .map(p -> enrichProductResponse(productMapper.toResponse(p)))
                    .toList();
            long total = mongoTemplate.count(Query.query(finalCriteria), Product.class);
            return new PageImpl<>(content, safePageable, total);
        }

        Query query = Query.query(finalCriteria).with(safePageable.getSort());
        List<ProductResponse> filtered = mongoTemplate.find(query, Product.class).stream()
            .map(p -> enrichProductResponse(productMapper.toResponse(p)))
                .filter(response -> matchesRatingRange(response.getAverageRating(), minRating, maxRating))
                .toList();

        int pageSize = safePageable.getPageSize();
        int from = (int) safePageable.getOffset();
        if (from >= filtered.size()) {
            return new PageImpl<>(List.of(), safePageable, filtered.size());
        }
        int to = Math.min(from + pageSize, filtered.size());
        return new PageImpl<>(filtered.subList(from, to), safePageable, filtered.size());
    }

    public Page<ProductResponse> getProducts(Pageable pageable) {
        return getProducts(null, null, null, null, null, null, null, false, Map.of(), pageable);
    }

    public Page<ProductResponse> getFeaturedProducts(Pageable pageable) {
        Pageable safePageable = sanitizePageable(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Query query = Query.query(Criteria.where("active").is(true));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        List<ProductResponse> all = mongoTemplate.find(query, Product.class).stream()
            .map(p -> enrichProductResponse(productMapper.toResponse(p)))
                .sorted(featuredComparator())
                .toList();

        int from = (int) safePageable.getOffset();
        if (from >= all.size()) {
            return new PageImpl<>(List.of(), safePageable, all.size());
        }
        int to = Math.min(from + safePageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), safePageable, all.size());
    }

    public Page<ProductResponse> searchProducts(String keyword,
                                                 String categoryId,
                                                 String categorySlug,
                                                 String brandId,
                                                 BigDecimal minPrice,
                                                 BigDecimal maxPrice,
                                                 Double minRating,
                                                 Double maxRating,
                                                 Map<String, List<String>> specsFilters,
                                                 Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return getProducts(
                    categoryId,
                    categorySlug,
                    brandId,
                    minPrice,
                    maxPrice,
                    minRating,
                    maxRating,
                    false,
                    specsFilters,
                    pageable);
        }

        String normalizedKeyword = keyword.trim();
        validatePriceRange(minPrice, maxPrice);

        // Build extra Criteria for additional filters
        List<Criteria> extra = new ArrayList<>();
        extra.add(Criteria.where("active").is(true));
        String resolvedCategoryId = resolveCategoryId(categoryId, categorySlug);
        if (StringUtils.hasText(resolvedCategoryId)) {
            extra.add(categoryArrayCriteria(resolvedCategoryId));
        }
        if (StringUtils.hasText(brandId)) {
            validateBrand(brandId);
            extra.add(idCriteria("brandId", brandId));
        }
        if (minPrice != null || maxPrice != null) {
            Criteria priceCriteria = Criteria.where("price");
            if (minPrice != null) priceCriteria = priceCriteria.gte(minPrice);
            if (maxPrice != null) priceCriteria = priceCriteria.lte(maxPrice);
            extra.add(priceCriteria);
        }
        extra.addAll(buildSpecsCriteria(specsFilters));

        Criteria combinedCriteria = extra.size() == 1
                ? extra.getFirst()
                : new Criteria().andOperator(extra.toArray(new Criteria[0]));

        boolean hasRatingFilter = minRating != null || maxRating != null;

        try {
            TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matching(normalizedKeyword);
            Query query = TextQuery.queryText(textCriteria).sortByScore()
                    .with(hasRatingFilter ? Pageable.unpaged() : pageable)
                    .addCriteria(combinedCriteria);

            List<ProductResponse> hits = mongoTemplate.find(query, Product.class).stream()
                    .map(p -> enrichProductResponse(productMapper.toResponse(p)))
                    .toList();

            if (!hits.isEmpty()) {
                if (hasRatingFilter) {
                    List<ProductResponse> filtered = hits.stream()
                            .filter(r -> matchesRatingRange(r.getAverageRating(), minRating, maxRating))
                            .toList();
                    return pageSlice(filtered, pageable);
                }
                long total = mongoTemplate.count(
                        TextQuery.queryText(textCriteria).addCriteria(combinedCriteria), Product.class);
                return new PageImpl<>(hits, pageable, total);
            }
        } catch (UncategorizedMongoDbException ex) {
            // fall through to regex fallback
        }

        // Regex fallback (no text index)
        extra.add(Criteria.where("name").regex(normalizedKeyword, "i"));
        Criteria regexCriteria = new Criteria().andOperator(extra.toArray(new Criteria[0]));
        Query regexQuery = Query.query(regexCriteria).with(hasRatingFilter ? Pageable.unpaged() : pageable);

        if (hasRatingFilter) {
            List<ProductResponse> all = mongoTemplate.find(regexQuery, Product.class).stream()
                    .map(p -> enrichProductResponse(productMapper.toResponse(p)))
                    .filter(r -> matchesRatingRange(r.getAverageRating(), minRating, maxRating))
                    .toList();
            return pageSlice(all, pageable);
        }

        List<ProductResponse> content = mongoTemplate.find(regexQuery, Product.class).stream()
                .map(p -> enrichProductResponse(productMapper.toResponse(p)))
                .toList();
        long total = mongoTemplate.count(Query.query(regexCriteria), Product.class);
        return new PageImpl<>(content, pageable, total);
    }

    public List<ProductSuggestResponse> suggestProducts(String q) {
        if (!StringUtils.hasText(q) || q.trim().length() < 1) {
            return List.of();
        }
        String escaped = Pattern.quote(q.trim());
        Pattern regex = Pattern.compile(escaped, Pattern.CASE_INSENSITIVE);
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("active").is(true),
                Criteria.where("name").regex(regex)
        ));
        query.fields().include("name").include("slug").include("thumbnail").include("price");
        query.limit(5);
        return mongoTemplate.find(query, Product.class).stream()
            .map(p -> ProductSuggestResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .slug(p.getSlug())
                .thumbnail(p.getThumbnail())
                .price(p.getPrice())
                .build())
            .toList();
    }

            public Page<ProductResponse> getProductsByTags(List<String> tags, Pageable pageable) {
            List<String> normalizedTags = normalizeTags(tags);
            if (normalizedTags.isEmpty()) {
                throw new BadRequestException("tags must not be empty");
            }

            Pageable safePageable = sanitizePageable(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
            Criteria finalCriteria = new Criteria().andOperator(
                Criteria.where("active").is(true),
                anyTagCriteria(normalizedTags));

            Query query = Query.query(finalCriteria).with(safePageable);
            List<ProductResponse> content = mongoTemplate.find(query, Product.class).stream()
                .map(p -> enrichProductResponse(productMapper.toResponse(p)))
                .toList();

            long total = mongoTemplate.count(Query.query(finalCriteria), Product.class);
            return new PageImpl<>(content, safePageable, total);
            }

    public Page<ProductResponse> getProductsByCategory(String categoryId, Pageable pageable) {
        validateCategory(categoryId);

        Query query = Query.query(new Criteria().andOperator(
                        Criteria.where("active").is(true),
                        categoryArrayCriteria(categoryId)))
                .with(pageable);

        List<ProductResponse> content = mongoTemplate.find(query, Product.class).stream()
            .map(p -> enrichProductResponse(productMapper.toResponse(p)))
                .toList();
        long total = mongoTemplate.count(
                Query.query(new Criteria().andOperator(
                        Criteria.where("active").is(true),
                        categoryArrayCriteria(categoryId))),
                Product.class);

        return new PageImpl<>(content, pageable, total);
    }

    public Page<ProductResponse> getProductsByCategorySlug(String slug,
                                                            String brandId,
                                                            BigDecimal minPrice,
                                                            BigDecimal maxPrice,
                                                            Double minRating,
                                                            Double maxRating,
                                                            Boolean featured,
                                                            Map<String, List<String>> specsFilters,
                                                            Pageable pageable) {
        Category category = categoryRepository.findBySlug(slug)
                .filter(Category::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug));

        // Merge category-level filter config with caller-supplied params.
        // Caller params always win (explicit > inferred).
        String resolvedBrandId = brandId;
        BigDecimal resolvedMinPrice = minPrice;
        BigDecimal resolvedMaxPrice = maxPrice;

        com.nexatech.product.model.CategoryFilter cf = category.getFilter();
        if (cf != null) {
            // Inject brandSlug from category filter when caller did not supply brandId
            if (!StringUtils.hasText(resolvedBrandId) && StringUtils.hasText(cf.getBrandSlug())) {
                resolvedBrandId = brandRepository.findBySlug(cf.getBrandSlug())
                        .filter(Brand::isActive)
                        .map(Brand::getId)
                        .orElse(null);
            }
            // Apply default price range from category filter when caller did not supply limits
            if (cf.getDefaultPriceRange() != null) {
                if (resolvedMinPrice == null) {
                    resolvedMinPrice = cf.getDefaultPriceRange().getMin();
                }
                if (resolvedMaxPrice == null) {
                    resolvedMaxPrice = cf.getDefaultPriceRange().getMax();
                }
            }
        }

        return getProducts(
                category.getId(),
                null,
                resolvedBrandId,
                resolvedMinPrice,
                resolvedMaxPrice,
                minRating,
                maxRating,
                featured,
                specsFilters,
                pageable);
    }

    public List<ProductResponse> getRelatedProducts(String productId) {
        Product product = getActiveProduct(productId);
        if (!StringUtils.hasText(product.getPrimaryCategoryId())) {
            return List.of();
        }

        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("active").is(true));
        criteriaList.add(categoryArrayCriteria(product.getPrimaryCategoryId()));
        criteriaList.add(idNotEqualCriteria("_id", productId));

        Query query = Query.query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        return mongoTemplate.find(query, Product.class).stream()
            .map(p -> enrichProductResponse(productMapper.toResponse(p)))
                .sorted(featuredComparator())
                .limit(RELATED_PRODUCT_LIMIT)
                .toList();
    }

    public void deleteProduct(String id) {
        Product product = getActiveProduct(id);
        product.setActive(false);
        Product saved = productRepository.save(product);
        publishEvent(saved, "PRODUCT_DELETED");
    }

    private Product getActiveProduct(String id) {
        Query query = Query.query(new Criteria().andOperator(
                MongoIdUtils.idEqualsCriteria("_id", id),
                Criteria.where("active").is(true)
        ));
        Product product = mongoTemplate.findOne(query, Product.class);
        if (product == null) {
            throw new ResourceNotFoundException("Product", "id", id);
        }
        return product;
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

    private ProductResponse enrichProductResponse(ProductResponse response) {
        if (StringUtils.hasText(response.getPrimaryCategoryId())) {
            categoryRepository.findById(response.getPrimaryCategoryId()).ifPresent(category -> {
                response.setPrimaryCategoryName(category.getName());
            });
        }

        if (StringUtils.hasText(response.getBrandId())) {
            brandRepository.findById(response.getBrandId()).ifPresent(brand -> {
                response.setBrandName(brand.getName());
                response.setBrandSlug(brand.getSlug());
            });
        }

        Double avg = reviewRepository.getAverageRatingByProductId(response.getId());
        if (avg != null) {
            response.setAverageRating(Math.round(avg * 10.0) / 10.0);

                try {
                    InventoryStockResponse inv = inventoryClient.getStock(response.getId());
                    if (inv != null && inv.getData() != null) {
                        int available = inv.getData().getAvailableQuantity();
                        int reorder = inv.getData().getReorderLevel();
                        response.setStockQuantity(available);
                        if (available <= 0) {
                            response.setStockStatus("OUT_OF_STOCK");
                        } else if (available <= reorder) {
                            response.setStockStatus("LOW_STOCK");
                        } else {
                            response.setStockStatus("IN_STOCK");
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not fetch inventory for product {}: {}", response.getId(), e.getMessage());
                }

        }
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
            if (existing == null || MongoIdUtils.equalsId(currentProductId, existing.getId())) {
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

    private String resolveThumbnail(String requestedThumbnail, List<String> images) {
        if (StringUtils.hasText(requestedThumbnail)) {
            return requestedThumbnail;
        }
        if (images != null && !images.isEmpty()) {
            return images.getFirst();
        }
        return null;
    }

    private Criteria idCriteria(String fieldName, String idValue) {
        return MongoIdUtils.idEqualsCriteria(fieldName, idValue);
    }

    private Criteria categoryArrayCriteria(String categoryId) {
        // Backward-compatible match for mixed String/ObjectId data in categoryIds array.
        return MongoIdUtils.arrayContainsIdCriteria("categoryIds", categoryId);
    }

    private String resolveCategoryId(String categoryId, String categorySlug) {
        if (StringUtils.hasText(categoryId)) {
            validateCategory(categoryId);
            return categoryId;
        }

        if (!StringUtils.hasText(categorySlug)) {
            return null;
        }

        return categoryRepository.findBySlug(categorySlug)
                .filter(Category::isActive)
                .map(Category::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", categorySlug));
    }

    private List<Criteria> buildSpecsCriteria(Map<String, List<String>> specsFilters) {
        if (specsFilters == null || specsFilters.isEmpty()) {
            return List.of();
        }

        List<Criteria> criteria = new ArrayList<>();
        specsFilters.forEach((key, values) -> {
            if (!StringUtils.hasText(key) || values == null || values.isEmpty()) {
                return;
            }

            String specField = "specs." + key.trim();
            List<Criteria> valueCriterias = values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(value -> Criteria.where(specField).regex("^" + Pattern.quote(value) + "$", "i"))
                    .toList();

            if (valueCriterias.isEmpty()) {
                return;
            }

            if (valueCriterias.size() == 1) {
                criteria.add(valueCriterias.getFirst());
            } else {
                criteria.add(new Criteria().orOperator(valueCriterias.toArray(new Criteria[0])));
            }
        });

        return criteria;
    }

    private Criteria idNotEqualCriteria(String fieldName, String idValue) {
        return MongoIdUtils.idNotEqualsCriteria(fieldName, idValue);
    }

    private Criteria anyTagCriteria(List<String> tags) {
        List<Criteria> tagCriteria = tags.stream()
                .map(tag -> Criteria.where("tags").regex("^" + Pattern.quote(tag) + "$", "i"))
                .toList();
        return new Criteria().orOperator(tagCriteria.toArray(new Criteria[0]));
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            normalized.add(tag.trim());
        }
        return List.copyOf(normalized);
    }

    private boolean matchesRatingRange(Double rating, Double minRating, Double maxRating) {
        double value = rating == null ? 0.0 : rating;
        if (minRating != null && value < minRating) {
            return false;
        }
        if (maxRating != null && value > maxRating) {
            return false;
        }
        return true;
    }

    private Comparator<ProductResponse> featuredComparator() {
        return Comparator
                .comparing((ProductResponse p) -> p.getAverageRating() == null ? 0.0 : p.getAverageRating())
                .reversed()
                .thenComparing(ProductResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private Pageable sanitizePageable(Pageable pageable, Sort defaultSort) {
        int page = pageable == null ? 0 : pageable.getPageNumber();
        int size = pageable == null ? 20 : pageable.getPageSize();

        Sort safeSort = defaultSort;
        if (pageable != null && pageable.getSort().isSorted()) {
            List<Sort.Order> accepted = pageable.getSort().stream()
                    .filter(order -> ALLOWED_SORT_FIELDS.contains(order.getProperty()))
                    .map(order -> new Sort.Order(order.getDirection(), order.getProperty()))
                    .toList();
            if (!accepted.isEmpty()) {
                safeSort = Sort.by(accepted);
            }
        }

        return PageRequest.of(Math.max(page, 0), Math.max(size, 1), safeSort);
    }

    private Page<ProductResponse> pageSlice(List<ProductResponse> list, Pageable pageable) {
        int from = (int) pageable.getOffset();
        if (from >= list.size()) {
            return new PageImpl<>(List.of(), pageable, list.size());
        }
        int to = Math.min(from + pageable.getPageSize(), list.size());
        return new PageImpl<>(list.subList(from, to), pageable, list.size());
    }

    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("Invalid price range: minPrice must be <= maxPrice");
        }
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
