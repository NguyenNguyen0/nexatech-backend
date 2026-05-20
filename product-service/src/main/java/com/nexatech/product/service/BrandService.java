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
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final BrandMapper brandMapper;
    private final MongoTemplate mongoTemplate;

    public BrandResponse createBrand(BrandRequest request) {
        String normalizedName = normalizeName(request.getName());
        if (brandRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new BadRequestException("Brand name already exists: " + normalizedName);
        }

        validateParentBrand(request.getParentBrandId(), null);

        Brand brand = brandMapper.toEntity(request);
        brand.setName(normalizedName);
        brand.setSlug(generateUniqueSlug(normalizedName, null));
        brand.setActive(true);

        return brandMapper.toResponse(brandRepository.save(brand));
    }

    public BrandResponse updateBrand(String id, BrandRequest request) {
        Brand brand = getActiveBrand(id);
        String normalizedName = normalizeName(request.getName());

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

        return brandMapper.toResponse(brandRepository.save(brand));
    }

    public BrandResponse getBrand(String id) {
        return brandMapper.toResponse(getActiveBrand(id));
    }

    public BrandResponse getBrandBySlug(String slug) {
        Brand brand = brandRepository.findBySlug(slug)
                .filter(Brand::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", "slug", slug));
        return brandMapper.toResponse(brand);
    }

    public List<BrandResponse> getAllBrands() {
        return brandRepository.findByActiveTrue().stream()
                .map(brandMapper::toResponse)
                .toList();
    }

    public void deleteBrand(String id) {
        Brand brand = getActiveBrand(id);

        if (productRepository.existsByBrandIdAndActiveTrue(id)) {
            throw new BadRequestException("Cannot delete brand with active products");
        }

        brand.setActive(false);
        brandRepository.save(brand);
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

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
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
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        String slug = normalized
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        return StringUtils.hasText(slug) ? slug : "brand";
    }
}
