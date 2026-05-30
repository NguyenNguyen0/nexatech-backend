package com.nexatech.product.util;

import org.springframework.data.domain.Pageable;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProductCacheKey {

    private ProductCacheKey() {
    }

    public static String list(Object... parts) {
        List<String> normalized = new ArrayList<>();
        for (Object part : parts) {
            normalized.add(normalize(part));
        }
        return String.join("|", normalized);
    }

    private static String normalize(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof Pageable pageable) {
            return "page=" + pageable.getPageNumber()
                    + ",size=" + pageable.getPageSize()
                    + ",sort=" + pageable.getSort();
        }
        if (value instanceof MultiValueMap<?, ?> multiValueMap) {
            return normalizeMap(multiValueMap);
        }
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        return String.valueOf(value).trim();
    }

    private static String normalizeMap(Map<?, ?> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((left, right) -> String.valueOf(left).compareTo(String.valueOf(right))))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "," + right)
                .orElse("-");
    }
}
