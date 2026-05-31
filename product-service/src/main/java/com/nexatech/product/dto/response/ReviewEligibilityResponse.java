package com.nexatech.product.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReviewEligibilityResponse {

    private int purchasedCount;
    private int reviewCount;
    private int remainingCount;
    private boolean canReview;
    private List<ReviewResponse> reviews;
}
