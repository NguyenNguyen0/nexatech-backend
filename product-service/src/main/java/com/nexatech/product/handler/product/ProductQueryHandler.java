package com.nexatech.product.handler.product;

import com.nexatech.product.dto.response.ProductMinimalResponse;
import com.nexatech.product.dto.response.ProductResponse;
import com.nexatech.product.exception.BadRequestException;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ProductMapper;
import com.nexatech.product.model.Category;
import com.nexatech.product.model.Product;
import com.nexatech.product.query.product.*;
import com.nexatech.product.repository.CategoryRepository;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.service.ProductResponseEnricher;
import com.nexatech.product.util.MongoIdUtils;
import com.nexatech.product.util.ProductQueryUtils;
import com.nexatech.product.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ProductQueryHandler {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("price", "name", "createdAt", "updatedAt", "sku");
    private static final int RELATED_PRODUCT_LIMIT = 8;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ProductResponseEnricher productResponseEnricher;
    private final MongoTemplate mongoTemplate;

    @Cacheable(value = "products", key = "#query.id()")
    public ProductResponse handle(GetProductByIdQuery query) {
        return productResponseEnricher.enrichCatalogResponse(
                productMapper.toResponse(getActiveProduct(query.id())));
    }

    @Cacheable(value = "products", key = "'slug:' + #query.slug()")
    public ProductResponse handle(GetProductBySlugQuery query) {
        Product product = productRepository.findBySlug(query.slug())
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", query.slug()));
        return productResponseEnricher.enrichCatalogResponse(productMapper.toResponse(product));
    }

    public Page<ProductMinimalResponse> handle(GetProductsQuery query, Pageable pageable) {
        if (Boolean.TRUE.equals(query.featured())) {
            return getFeaturedProductsMinimal(pageable);
        }

        ProductQueryUtils.validatePriceRange(query.minPrice(), query.maxPrice());
        Pageable safePageable = sanitizePageable(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<Criteria> criteriaList = new ArrayList<>();
        appendActiveCriteria(criteriaList, query.active(), query.includeDeleted());

        String resolvedCategoryId = resolveCategoryId(query.categoryId(), query.categorySlug());
        if (StringUtils.hasText(resolvedCategoryId)) {
            criteriaList.add(categoryArrayCriteria(resolvedCategoryId));
        }
        if (StringUtils.hasText(query.brandId())) {
            criteriaList.add(idCriteria("brandId", query.brandId()));
        }
        if (StringUtils.hasText(query.sku())) {
            criteriaList.add(Criteria.where("sku").regex(Pattern.quote(query.sku().trim()), "i"));
        }
        if (StringUtils.hasText(query.keyword())) {
            Pattern keywordPattern = Pattern.compile(Pattern.quote(query.keyword().trim()), Pattern.CASE_INSENSITIVE);
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("name").regex(keywordPattern),
                    Criteria.where("sku").regex(keywordPattern),
                    Criteria.where("description").regex(keywordPattern)
            ));
        }
        if (query.minPrice() != null || query.maxPrice() != null) {
            criteriaList.add(ProductQueryUtils.buildPriceCriteria(query.minPrice(), query.maxPrice()));
        }
        criteriaList.addAll(ProductQueryUtils.buildSpecsCriteria(query.specsFilters()));

        Criteria finalCriteria = criteriaList.size() == 1
                ? criteriaList.getFirst()
                : new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));

        boolean hasRatingFilter = query.minRating() != null || query.maxRating() != null;
        if (!hasRatingFilter) {
            Query q = Query.query(finalCriteria).with(safePageable);
            List<ProductMinimalResponse> content = toMinimalCatalogResponses(mongoTemplate.find(q, Product.class));
            long total = mongoTemplate.count(Query.query(finalCriteria), Product.class);
            return new PageImpl<>(content, safePageable, total);
        }

        Query q = Query.query(finalCriteria).with(safePageable.getSort());
        List<ProductMinimalResponse> filtered = toMinimalCatalogResponses(mongoTemplate.find(q, Product.class)).stream()
                .filter(r -> ProductQueryUtils.matchesRatingRange(r.getAverageRating(), query.minRating(), query.maxRating()))
                .toList();

        int pageSize = safePageable.getPageSize();
        int from = (int) safePageable.getOffset();
        if (from >= filtered.size()) {
            return new PageImpl<>(List.of(), safePageable, filtered.size());
        }
        int to = Math.min(from + pageSize, filtered.size());
        return new PageImpl<>(filtered.subList(from, to), safePageable, filtered.size());
    }

    public Page<ProductMinimalResponse> handle(SearchProductsQuery query, Pageable pageable) {
        if (!StringUtils.hasText(query.keyword())) {
            GetProductsQuery fallback = new GetProductsQuery(
                    null, query.categoryId(), query.categorySlug(), query.brandId(),
                    null, query.minPrice(), query.maxPrice(),
                    query.minRating(), query.maxRating(),
                    false, null, false, query.specsFilters());
            return handle(fallback, pageable);
        }

        String normalizedKeyword = query.keyword().trim();
        ProductQueryUtils.validatePriceRange(query.minPrice(), query.maxPrice());

        List<Criteria> extra = new ArrayList<>();
        extra.add(Criteria.where("active").is(true));
        String resolvedCategoryId = resolveCategoryId(query.categoryId(), query.categorySlug());
        if (StringUtils.hasText(resolvedCategoryId)) {
            extra.add(categoryArrayCriteria(resolvedCategoryId));
        }
        if (StringUtils.hasText(query.brandId())) {
            extra.add(idCriteria("brandId", query.brandId()));
        }
        if (query.minPrice() != null || query.maxPrice() != null) {
            extra.add(ProductQueryUtils.buildPriceCriteria(query.minPrice(), query.maxPrice()));
        }
        extra.addAll(ProductQueryUtils.buildSpecsCriteria(query.specsFilters()));

        Criteria combinedCriteria = extra.size() == 1
                ? extra.getFirst()
                : new Criteria().andOperator(extra.toArray(new Criteria[0]));

        boolean hasRatingFilter = query.minRating() != null || query.maxRating() != null;

        try {
            TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matching(normalizedKeyword);
            Query q = TextQuery.queryText(textCriteria).sortByScore()
                    .with(hasRatingFilter ? Pageable.unpaged() : pageable)
                    .addCriteria(combinedCriteria);

            List<ProductMinimalResponse> hits = toMinimalCatalogResponses(mongoTemplate.find(q, Product.class));

            if (!hits.isEmpty()) {
                if (hasRatingFilter) {
                    List<ProductMinimalResponse> filtered = hits.stream()
                            .filter(r -> ProductQueryUtils.matchesRatingRange(r.getAverageRating(), query.minRating(), query.maxRating()))
                            .toList();
                    return ProductQueryUtils.pageSlice(filtered, pageable);
                }
                long total = mongoTemplate.count(
                        TextQuery.queryText(textCriteria).addCriteria(combinedCriteria), Product.class);
                return new PageImpl<>(hits, pageable, total);
            }
        } catch (UncategorizedMongoDbException ex) {
            // fall through to regex fallback
        }

        // Regex fallback
        extra.add(Criteria.where("name").regex(normalizedKeyword, "i"));
        Criteria regexCriteria = new Criteria().andOperator(extra.toArray(new Criteria[0]));
        Query regexQuery = Query.query(regexCriteria).with(hasRatingFilter ? Pageable.unpaged() : pageable);

        if (hasRatingFilter) {
            List<ProductMinimalResponse> all = toMinimalCatalogResponses(mongoTemplate.find(regexQuery, Product.class)).stream()
                    .filter(r -> ProductQueryUtils.matchesRatingRange(r.getAverageRating(), query.minRating(), query.maxRating()))
                    .toList();
            return ProductQueryUtils.pageSlice(all, pageable);
        }

        List<ProductMinimalResponse> content = toMinimalCatalogResponses(mongoTemplate.find(regexQuery, Product.class));
        long total = mongoTemplate.count(Query.query(regexCriteria), Product.class);
        return new PageImpl<>(content, pageable, total);
    }

    public Page<ProductResponse> handle(GetFeaturedProductsQuery query, Pageable pageable) {
        Pageable safePageable = sanitizePageable(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Query q = Query.query(Criteria.where("active").is(true));
        q.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        List<ProductResponse> all = mongoTemplate.find(q, Product.class).stream()
                .map(p -> productResponseEnricher.enrichCatalogResponse(productMapper.toResponse(p)))
                .sorted(featuredComparatorForResponse())
                .toList();

        int from = (int) safePageable.getOffset();
        if (from >= all.size()) {
            return new PageImpl<>(List.of(), safePageable, all.size());
        }
        int to = Math.min(from + safePageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), safePageable, all.size());
    }

    public Page<ProductResponse> handle(GetProductsByCategoryQuery query, Pageable pageable) {
        validateCategory(query.categoryId());

        Query q = Query.query(new Criteria().andOperator(
                        Criteria.where("active").is(true),
                        categoryArrayCriteria(query.categoryId())))
                .with(pageable);

        List<ProductResponse> content = mongoTemplate.find(q, Product.class).stream()
                .map(p -> productResponseEnricher.enrichCatalogResponse(productMapper.toResponse(p)))
                .toList();
        long total = mongoTemplate.count(
                Query.query(new Criteria().andOperator(
                        Criteria.where("active").is(true),
                        categoryArrayCriteria(query.categoryId()))),
                Product.class);

        return new PageImpl<>(content, pageable, total);
    }

    public Page<ProductMinimalResponse> handle(GetProductsByCategorySlugQuery query, Pageable pageable) {
        Category category = categoryRepository.findBySlug(query.slug())
                .filter(Category::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", query.slug()));

        String resolvedBrandId = query.brandId();
        BigDecimal resolvedMinPrice = query.minPrice();
        BigDecimal resolvedMaxPrice = query.maxPrice();

        com.nexatech.product.model.CategoryFilter cf = category.getFilter();
        if (cf != null) {
            if (!StringUtils.hasText(resolvedBrandId) && StringUtils.hasText(cf.getBrandSlug())) {
                resolvedBrandId = resolveBrandIdBySlug(cf.getBrandSlug());
            }
            if (cf.getDefaultPriceRange() != null) {
                if (resolvedMinPrice == null) {
                    resolvedMinPrice = cf.getDefaultPriceRange().getMin();
                }
                if (resolvedMaxPrice == null) {
                    resolvedMaxPrice = cf.getDefaultPriceRange().getMax();
                }
            }
        }

        GetProductsQuery delegate = new GetProductsQuery(
                null, category.getId(), null, resolvedBrandId, null,
                resolvedMinPrice, resolvedMaxPrice,
                query.minRating(), query.maxRating(),
                query.featured(), null, false, query.specsFilters());
        return handle(delegate, pageable);
    }

    public List<ProductMinimalResponse> handle(GetRandomProductsByCategorySlugQuery query) {
        Category category = categoryRepository.findBySlug(query.slug())
                .filter(Category::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", query.slug()));

        int safeLimit = Math.max(1, Math.min(query.limit(), 12));
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("active").is(true),
                categoryArrayCriteria(category.getId()));
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.sample(safeLimit));
        AggregationResults<Product> results = mongoTemplate.aggregate(aggregation, "products", Product.class);

        return toMinimalCatalogResponses(results.getMappedResults());
    }

    public Page<ProductMinimalResponse> handle(GetProductsByTagsQuery query, Pageable pageable) {
        List<String> normalizedTags = TextUtils.normalizeDistinct(query.tags());
        if (normalizedTags.isEmpty()) {
            throw new BadRequestException("tags must not be empty");
        }

        Pageable safePageable = sanitizePageable(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Criteria finalCriteria = new Criteria().andOperator(
                Criteria.where("active").is(true),
                ProductQueryUtils.anyTagCriteria(normalizedTags));

        Query q = Query.query(finalCriteria).with(safePageable);
        List<ProductMinimalResponse> content = toMinimalCatalogResponses(mongoTemplate.find(q, Product.class));
        long total = mongoTemplate.count(Query.query(finalCriteria), Product.class);
        return new PageImpl<>(content, safePageable, total);
    }

    public List<ProductMinimalResponse> handle(GetRelatedProductsQuery query) {
        return getRelatedProductsFromSource(getActiveProduct(query.productId()));
    }

    public List<ProductMinimalResponse> handle(GetRelatedProductsBySlugQuery query) {
        Product product = productRepository.findBySlug(query.slug())
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", query.slug()));
        return getRelatedProductsFromSource(product);
    }

    public Page<ProductMinimalResponse> handle(GetActiveProductsForSyncQuery query, Pageable pageable) {
        Pageable safePageable = sanitizePageable(pageable, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return productRepository.findByActiveTrue(safePageable).map(this::toMinimalSyncResponse);
    }

    public List<ProductMinimalResponse> handle(LookupProductsByIdsQuery query) {
        List<String> normalizedIds = TextUtils.normalizeDistinct(query.ids());
        if (normalizedIds.isEmpty()) {
            return List.of();
        }

        Map<String, Product> byId = productRepository.findByIdInAndActiveTrue(normalizedIds).stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));

        List<ProductMinimalResponse> responses = new ArrayList<>();
        for (String id : normalizedIds) {
            Product product = byId.get(id);
            if (product != null) {
                responses.add(toMinimalSyncResponse(product));
            }
        }
        return responses;
    }

    // ---------- private helpers ----------

    private Product getActiveProduct(String id) {
        return productRepository.findById(id)
            .filter(Product::isActive)
            .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    private List<ProductMinimalResponse> getRelatedProductsFromSource(Product product) {
        if (!StringUtils.hasText(product.getPrimaryCategoryId())) {
            return List.of();
        }

        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("active").is(true));
        criteriaList.add(categoryArrayCriteria(product.getPrimaryCategoryId()));
        criteriaList.add(MongoIdUtils.idNotEqualsCriteria("_id", product.getId()));

        Query query = Query.query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        return toMinimalCatalogResponses(mongoTemplate.find(query, Product.class)).stream()
                .sorted(featuredComparatorForMinimal())
                .limit(RELATED_PRODUCT_LIMIT)
                .toList();
    }

    private ProductMinimalResponse toMinimalCatalogResponse(Product product) {
        return productResponseEnricher.enrichMinimalCatalogResponse(toMinimalBaseResponse(product));
    }

    private List<ProductMinimalResponse> toMinimalCatalogResponses(List<Product> products) {
        List<ProductMinimalResponse> responses = products.stream()
                .map(this::toMinimalBaseResponse)
                .toList();
        return productResponseEnricher.enrichMinimalCatalogResponses(responses);
    }

    private ProductMinimalResponse toMinimalBaseResponse(Product product) {
        ProductMinimalResponse response = productMapper.toMinimalResponse(product);
        if (response.getOriginalPrice() == null) {
            response.setOriginalPrice(response.getCompareAtPrice());
        }
        return response;
    }

    private ProductMinimalResponse toMinimalSyncResponse(Product product) {
        ProductMinimalResponse response = productMapper.toMinimalResponse(product);
        if (response.getOriginalPrice() == null) {
            response.setOriginalPrice(response.getCompareAtPrice());
        }
        return response;
    }

    private Page<ProductMinimalResponse> getFeaturedProductsMinimal(Pageable pageable) {
        Pageable safePageable = sanitizePageable(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Query query = Query.query(Criteria.where("active").is(true));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        List<ProductMinimalResponse> all = toMinimalCatalogResponses(mongoTemplate.find(query, Product.class)).stream()
                .sorted(featuredComparatorForMinimal())
                .toList();

        int from = (int) safePageable.getOffset();
        if (from >= all.size()) {
            return new PageImpl<>(List.of(), safePageable, all.size());
        }
        int to = Math.min(from + safePageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), safePageable, all.size());
    }

    private void appendActiveCriteria(List<Criteria> criteriaList, Boolean active, boolean includeDeleted) {
        if (active != null) {
            criteriaList.add(Criteria.where("active").is(active));
            return;
        }
        if (!includeDeleted) {
            criteriaList.add(Criteria.where("active").is(true));
        }
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

    private String resolveBrandIdBySlug(String brandSlug) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("slug").is(brandSlug),
                Criteria.where("active").is(true)
        ));
        com.nexatech.product.model.Brand brand = mongoTemplate.findOne(query, com.nexatech.product.model.Brand.class);
        return brand != null ? brand.getId() : null;
    }

    private Criteria idCriteria(String fieldName, String idValue) {
        return MongoIdUtils.idEqualsCriteria(fieldName, idValue);
    }

    private Criteria categoryArrayCriteria(String categoryId) {
        // categoryIds is stored as plain string IDs across product-service.
        return MongoIdUtils.arrayContainsIdCriteria("categoryIds", categoryId);
    }

    private Pageable sanitizePageable(Pageable pageable, Sort defaultSort) {
        return ProductQueryUtils.sanitizePageable(pageable, defaultSort, ALLOWED_SORT_FIELDS);
    }

    private Comparator<ProductResponse> featuredComparatorForResponse() {
        return Comparator
                .comparing((ProductResponse p) -> p.getAverageRating() == null ? 0.0 : p.getAverageRating())
                .reversed()
                .thenComparing(ProductResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private Comparator<ProductMinimalResponse> featuredComparatorForMinimal() {
        return Comparator
                .comparing((ProductMinimalResponse p) -> p.getAverageRating() == null ? 0.0 : p.getAverageRating())
                .reversed()
                .thenComparing(ProductMinimalResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }
}
