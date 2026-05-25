package com.nexatech.product.util;

import com.nexatech.product.exception.BadRequestException;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProductQueryUtils {

    private ProductQueryUtils() {
    }

    public static Criteria buildPriceCriteria(BigDecimal minPrice, BigDecimal maxPrice) {
        Criteria priceCriteria = Criteria.where("price");
        if (minPrice != null) {
            priceCriteria = priceCriteria.gte(minPrice);
        }
        if (maxPrice != null) {
            priceCriteria = priceCriteria.lte(maxPrice);
        }
        return priceCriteria;
    }

    public static List<Criteria> buildSpecsCriteria(Map<String, List<String>> specsFilters) {
        if (specsFilters == null || specsFilters.isEmpty()) {
            return List.of();
        }

        List<Criteria> criteria = new ArrayList<>();
        specsFilters.forEach((key, values) -> {
            if (!StringUtils.hasText(key) || values == null || values.isEmpty()) {
                return;
            }

            String specField = "specs." + key.trim();
            List<Criteria> valueCriteria = TextUtils.normalizeDistinct(values).stream()
                    .map(value -> Criteria.where(specField).regex("^" + Pattern.quote(value) + "$", "i"))
                    .toList();

            if (valueCriteria.isEmpty()) {
                return;
            }

            if (valueCriteria.size() == 1) {
                criteria.add(valueCriteria.getFirst());
            } else {
                criteria.add(new Criteria().orOperator(valueCriteria.toArray(new Criteria[0])));
            }
        });

        return criteria;
    }

    public static Criteria anyTagCriteria(List<String> tags) {
        List<Criteria> tagCriteria = tags.stream()
                .map(tag -> Criteria.where("tags").regex("^" + Pattern.quote(tag) + "$", "i"))
                .toList();
        return new Criteria().orOperator(tagCriteria.toArray(new Criteria[0]));
    }

    public static boolean matchesRatingRange(Double rating, Double minRating, Double maxRating) {
        double value = rating == null ? 0.0 : rating;
        if (minRating != null && value < minRating) {
            return false;
        }
        return maxRating == null || value <= maxRating;
    }

    public static Pageable sanitizePageable(Pageable pageable, Sort defaultSort, Set<String> allowedSortFields) {
        int page = pageable == null ? 0 : pageable.getPageNumber();
        int size = pageable == null ? 20 : pageable.getPageSize();

        Sort safeSort = defaultSort;
        if (pageable != null && pageable.getSort().isSorted()) {
            List<Sort.Order> accepted = pageable.getSort().stream()
                    .filter(order -> allowedSortFields.contains(order.getProperty()))
                    .map(order -> new Sort.Order(order.getDirection(), order.getProperty()))
                    .toList();
            if (!accepted.isEmpty()) {
                safeSort = Sort.by(accepted);
            }
        }

        return PageRequest.of(Math.max(page, 0), Math.max(size, 1), safeSort);
    }

    public static <T> Page<T> pageSlice(List<T> list, Pageable pageable) {
        int from = (int) pageable.getOffset();
        if (from >= list.size()) {
            return new PageImpl<>(List.of(), pageable, list.size());
        }
        int to = Math.min(from + pageable.getPageSize(), list.size());
        return new PageImpl<>(list.subList(from, to), pageable, list.size());
    }

    public static void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("Invalid price range: minPrice must be <= maxPrice");
        }
    }
}
