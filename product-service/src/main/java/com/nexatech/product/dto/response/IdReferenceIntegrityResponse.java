package com.nexatech.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdReferenceIntegrityResponse {

    private boolean healthy;

    private long activeCategoryCount;
    private long activeBrandCount;
    private long activeProductCount;
    private long reviewCount;

    private long orphanCategoryParentRefs;
    private long orphanBrandParentRefs;
    private long orphanProductPrimaryCategoryRefs;
    private long orphanProductBrandRefs;
    private long orphanProductCategoryArrayRefs;
    private long orphanReviewProductRefs;

    private List<String> orphanCategoryParentSamples;
    private List<String> orphanBrandParentSamples;
    private List<String> orphanProductPrimaryCategorySamples;
    private List<String> orphanProductBrandSamples;
    private List<String> orphanProductCategoryArraySamples;
    private List<String> orphanReviewProductSamples;
}
