package com.nexatech.product.service;

import com.nexatech.product.dto.request.BrandRequest;
import com.nexatech.product.dto.response.BrandResponse;
import com.nexatech.product.exception.BadRequestException;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.BrandMapper;
import com.nexatech.product.model.Brand;
import com.nexatech.product.repository.BrandRepository;
import com.nexatech.product.repository.ProductRepository;
import com.nexatech.product.util.MongoIdUtils;
import com.nexatech.product.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final BrandMapper brandMapper;
    private final MongoTemplate mongoTemplate;
    private final CatalogCacheVersionService catalogCacheVersionService;

    @Caching(evict = {
            @CacheEvict(value = "brands", allEntries = true),
            @CacheEvict(value = "productPages", allEntries = true)
    })
    public BrandResponse createBrand(BrandRequest request) {
        String normalizedName = TextUtils.normalizeName(request.getName());
        if (brandRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new BadRequestException("Brand name already exists: " + normalizedName);
        }

        validateParentBrand(request.getParentBrandId(), null);

        Brand brand = brandMapper.toEntity(request);
        brand.setName(normalizedName);
        brand.setSlug(generateUniqueSlug(normalizedName, null));
        brand.setActive(true);

        BrandResponse response = brandMapper.toResponse(brandRepository.save(brand));
        catalogCacheVersionService.bumpVersion("BRAND_CREATED");
        return response;
    }

    @Caching(evict = {
            @CacheEvict(value = "brands", allEntries = true),
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productPages", allEntries = true)
    })
    public BrandResponse updateBrand(String id, BrandRequest request) {
        Brand brand = getActiveBrand(id);
        String normalizedName = TextUtils.normalizeName(request.getName());

        if (brandRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new BadRequestException("Brand name already exists: " + normalizedName);
        }

        validateParentBrand(request.getParentBrandId(), id);

        String oldName = brand.getName();
        brandMapper.updateEntity(request, brand);
        brand.setName(normalizedName);
        if (!normalizedName.equalsIgnoreCase(oldName)) {
            brand.setSlug(generateUniqueSlug(normalizedName, id));
        }

        BrandResponse response = brandMapper.toResponse(brandRepository.save(brand));
        catalogCacheVersionService.bumpVersion("BRAND_UPDATED");
        return response;
    }

    @Cacheable(value = "brands", key = "#id", unless = "#result == null")
    public BrandResponse getBrand(String id) {
        return brandMapper.toResponse(getActiveBrand(id));
    }

    @Cacheable(value = "brands", key = "'slug:' + #slug", unless = "#result == null")
    public BrandResponse getBrandBySlug(String slug) {
        Brand brand = brandRepository.findBySlug(slug)
                .filter(Brand::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", "slug", slug));
        return brandMapper.toResponse(brand);
    }

    @Cacheable(
            value = "brands",
            key = "'list:' + (#active == null ? 'all-active' : #active) + ':' + #includeDeleted",
            unless = "#result == null")
    public List<BrandResponse> getAllBrands(Boolean active, boolean includeDeleted) {
        Query query = new Query().with(Sort.by(Sort.Order.asc("name")));
        if (active != null) {
            query.addCriteria(Criteria.where("active").is(active));
        } else if (!includeDeleted) {
            query.addCriteria(Criteria.where("active").is(true));
        }

        return mongoTemplate.find(query, Brand.class).stream()
                .map(brandMapper::toResponse)
                .toList();
    }

    @Caching(evict = {
            @CacheEvict(value = "brands", allEntries = true),
            @CacheEvict(value = "productPages", allEntries = true)
    })
    public void deleteBrand(String id) {
        Brand brand = getActiveBrand(id);

        if (productRepository.existsByBrandIdAndActiveTrue(id)) {
            throw new BadRequestException("Cannot delete brand with active products");
        }

        brand.setActive(false);
        brandRepository.save(brand);
        catalogCacheVersionService.bumpVersion("BRAND_DELETED");
    }

    @Caching(evict = {
            @CacheEvict(value = "brands", allEntries = true),
            @CacheEvict(value = "productPages", allEntries = true)
    })
    public BrandResponse restoreBrand(String id) {
        Brand brand = getBrandById(id);
        if (brand.isActive()) {
            return brandMapper.toResponse(brand);
        }

        validateParentBrand(brand.getParentBrandId(), id);

        brand.setActive(true);
        BrandResponse response = brandMapper.toResponse(brandRepository.save(brand));
        catalogCacheVersionService.bumpVersion("BRAND_RESTORED");
        return response;
    }

    private Brand getActiveBrand(String id) {
        Query query = Query.query(new Criteria().andOperator(
                MongoIdUtils.idEqualsCriteria("_id", id),
                Criteria.where("active").is(true)
        ));
        Brand brand = mongoTemplate.findOne(query, Brand.class);
        if (brand == null) {
            throw new ResourceNotFoundException("Brand", "id", id);
        }
        return brand;
    }

    private Brand getBrandById(String id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", "id", id));
    }

    private void validateParentBrand(String parentBrandId, String currentBrandId) {
        if (!StringUtils.hasText(parentBrandId)) {
            return;
        }
        if (MongoIdUtils.equalsId(currentBrandId, parentBrandId)) {
            throw new BadRequestException("Brand cannot be its own parent");
        }
        getActiveBrand(parentBrandId);
    }

    private String generateUniqueSlug(String name, String currentBrandId) {
        String base = toSlug(name);
        String candidate = base;
        int suffix = 1;

        while (true) {
            Brand existing = brandRepository.findBySlug(candidate).orElse(null);
            if (existing == null || MongoIdUtils.equalsId(currentBrandId, existing.getId())) {
                return candidate;
            }
            candidate = base + "-" + suffix++;
        }
    }

    private String toSlug(String value) {
        return TextUtils.toSlug(value, "brand");
    }
}
