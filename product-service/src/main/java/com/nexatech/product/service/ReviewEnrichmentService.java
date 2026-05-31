package com.nexatech.product.service;

import com.nexatech.product.client.UserClient;
import com.nexatech.product.client.dto.UserProfileResponse;
import com.nexatech.product.client.dto.UserServiceApiResponse;
import com.nexatech.product.dto.response.ReviewResponse;
import com.nexatech.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewEnrichmentService {

    private final ProductRepository productRepository;
    private final UserClient userClient;

    public ReviewResponse enrich(ReviewResponse response) {
        enrichProductInfo(response);
        enrichUserInfo(response, new HashMap<>());
        return response;
    }

    public Page<ReviewResponse> enrichPage(Page<ReviewResponse> page) {
        Map<UUID, UserProfileResponse> userCache = new HashMap<>();
        page.getContent().forEach(response -> {
            enrichProductInfo(response);
            enrichUserInfo(response, userCache);
        });
        return page;
    }

    private void enrichProductInfo(ReviewResponse response) {
        if (!StringUtils.hasText(response.getProductId())) {
            return;
        }

        productRepository.findById(response.getProductId()).ifPresent(product -> {
            response.setProductName(product.getName());
            response.setProductSlug(product.getSlug());
            response.setProductThumbnail(product.getThumbnail());
        });
    }

    private void enrichUserInfo(ReviewResponse response, Map<UUID, UserProfileResponse> userCache) {
        UUID userId = response.getUserId();
        if (userId == null) {
            return;
        }

        UserProfileResponse profile = userCache.computeIfAbsent(userId, this::fetchUserProfile);
        if (profile == null) {
            return;
        }

        response.setUserName(resolveDisplayName(profile));
        response.setAvatar(profile.getAvatarUrl());
    }

    private UserProfileResponse fetchUserProfile(UUID userId) {
        try {
            UserServiceApiResponse<UserProfileResponse> apiResponse = userClient.getUserById(userId.toString());
            if (apiResponse == null || apiResponse.getResult() == null) {
                return null;
            }
            return apiResponse.getResult();
        } catch (Exception ex) {
            log.debug("Could not load user profile for review author {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    private static String resolveDisplayName(UserProfileResponse profile) {
        String first = profile.getFirstName() != null ? profile.getFirstName().trim() : "";
        String last = profile.getLastName() != null ? profile.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) {
            return full;
        }
        return StringUtils.hasText(profile.getUsername()) ? profile.getUsername().trim() : null;
    }
}
