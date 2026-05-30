package com.nexatech.inventory.util;

import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;

public final class InventoryCacheKey {

    private InventoryCacheKey() {
    }

    public static String of(Object... parts) {
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
        return String.valueOf(value).trim();
    }
}
