package com.nexatech.product.service;

import com.nexatech.product.client.dto.InventoryStockResponse;
import com.nexatech.product.dto.response.ProductMinimalResponse;
import com.nexatech.product.dto.response.ProductResponse;
import com.nexatech.product.model.Brand;
import com.nexatech.product.repository.BrandRepository;
import com.nexatech.product.repository.CategoryRepository;
import com.nexatech.product.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductResponseEnricher {

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ReviewRepository reviewRepository;
    private final ResilientDownstreamService resilientDownstreamService;

    public ProductResponse enrichCatalogResponse(ProductResponse response) {
        enrichPrimaryCategoryName(response);
        enrichBrandNameAndSlug(response);

        enrichAverageRating(response);
        enrichStock(response);
        return response;
    }

    public ProductMinimalResponse enrichMinimalCatalogResponse(ProductMinimalResponse response) {
        enrichMinimalMetadata(response);
        enrichStock(response);
        return response;
    }

    public List<ProductMinimalResponse> enrichMinimalCatalogResponses(List<ProductMinimalResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return List.of();
        }

        responses.forEach(this::enrichMinimalMetadata);
        enrichStocks(responses);
        return responses;
    }

    public ProductResponse enrichImageResponse(ProductResponse response) {
        enrichBrandName(response);
        enrichAverageRating(response);
        return response;
    }

    private void enrichPrimaryCategoryName(ProductResponse response) {
        if (StringUtils.hasText(response.getPrimaryCategoryId())) {
            categoryRepository.findById(response.getPrimaryCategoryId())
                    .ifPresent(category -> response.setPrimaryCategoryName(category.getName()));
        }
    }

    private void enrichPrimaryCategoryName(ProductMinimalResponse response) {
        if (StringUtils.hasText(response.getPrimaryCategoryId())) {
            categoryRepository.findById(response.getPrimaryCategoryId())
                    .ifPresent(category -> response.setPrimaryCategoryName(category.getName()));
        }
    }

    private void enrichBrandNameAndSlug(ProductResponse response) {
        if (StringUtils.hasText(response.getBrandId())) {
            brandRepository.findById(response.getBrandId()).ifPresent(brand -> {
                response.setBrandName(brand.getName());
                response.setBrandSlug(brand.getSlug());
            });
        }
    }

    private void enrichBrandNameAndSlug(ProductMinimalResponse response) {
        if (StringUtils.hasText(response.getBrandId())) {
            brandRepository.findById(response.getBrandId()).ifPresent(brand -> {
                response.setBrandName(brand.getName());
                response.setBrandSlug(brand.getSlug());
            });
        }
    }

    private void enrichBrandName(ProductResponse response) {
        if (StringUtils.hasText(response.getBrandId())) {
            response.setBrandName(
                    brandRepository.findById(response.getBrandId())
                            .map(Brand::getName)
                            .orElse(null));
        }
    }

    private Double enrichAverageRating(ProductResponse response) {
        Double avg = reviewRepository.getAverageRatingByProductId(response.getId());
        if (avg != null) {
            response.setAverageRating(Math.round(avg * 10.0) / 10.0);
        }
        return avg;
    }

    private Double enrichAverageRating(ProductMinimalResponse response) {
        Double avg = reviewRepository.getAverageRatingByProductId(response.getId());
        if (avg != null) {
            response.setAverageRating(Math.round(avg * 10.0) / 10.0);
        }
        return avg;
    }

    private void enrichReviewCount(ProductMinimalResponse response) {
        response.setReviewCount(reviewRepository.countByProductId(response.getId()));
    }

    private void enrichMinimalMetadata(ProductMinimalResponse response) {
        enrichPrimaryCategoryName(response);
        enrichBrandNameAndSlug(response);
        enrichAverageRating(response);
        enrichReviewCount(response);
    }

    private void enrichStock(ProductResponse response) {
        try {
            InventoryStockResponse inv = resilientDownstreamService.getStock(response.getId());
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
            } else {
                markOutOfStock(response);
            }
        } catch (Exception e) {
            log.debug("Could not fetch inventory for product {}: {}", response.getId(), e.getMessage());
            markOutOfStock(response);
        }
    }

    private void enrichStock(ProductMinimalResponse response) {
        try {
            InventoryStockResponse inv = resilientDownstreamService.getStock(response.getId());
            if (inv != null && inv.getData() != null) {
                applyStock(response, inv.getData());
            } else {
                markOutOfStock(response);
            }
        } catch (Exception e) {
            log.debug("Could not fetch inventory for product {}: {}", response.getId(), e.getMessage());
            markOutOfStock(response);
        }
    }

    private void enrichStocks(Collection<ProductMinimalResponse> responses) {
        List<String> productIds = responses.stream()
                .map(ProductMinimalResponse::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, InventoryStockResponse.StockData> stockByProductId = resilientDownstreamService.getStocks(productIds);

        responses.forEach(response -> {
            InventoryStockResponse.StockData stock = stockByProductId.get(response.getId());
            if (stock != null) {
                applyStock(response, stock);
            } else {
                markOutOfStock(response);
            }
        });
    }

    private void applyStock(ProductMinimalResponse response, InventoryStockResponse.StockData stock) {
        int available = stock.getAvailableQuantity();
        int reorder = stock.getReorderLevel();
        response.setStockQuantity(available);
        if (available <= 0) {
            response.setStockStatus("OUT_OF_STOCK");
        } else if (available <= reorder) {
            response.setStockStatus("LOW_STOCK");
        } else {
            response.setStockStatus("IN_STOCK");
        }
    }

    private void markOutOfStock(ProductResponse response) {
        response.setStockQuantity(0);
        response.setStockStatus("OUT_OF_STOCK");
    }

    private void markOutOfStock(ProductMinimalResponse response) {
        response.setStockQuantity(0);
        response.setStockStatus("OUT_OF_STOCK");
    }
}
