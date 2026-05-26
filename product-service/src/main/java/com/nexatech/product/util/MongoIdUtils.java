package com.nexatech.product.util;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.StringUtils;

public final class MongoIdUtils {

    private MongoIdUtils() {
    }

    public static boolean equalsId(Object left, Object right) {
        String leftId = normalize(left);
        String rightId = normalize(right);
        if (leftId == null || rightId == null) {
            return false;
        }
        return leftId.equals(rightId);
    }

    public static Criteria idEqualsCriteria(String fieldName, String idValue) {
        String normalized = normalize(idValue);
        if (normalized == null) {
            return Criteria.where(fieldName).is(idValue);
        }
        return Criteria.where(fieldName).is(normalized);
    }

    public static Criteria idNotEqualsCriteria(String fieldName, String idValue) {
        String normalized = normalize(idValue);
        if (normalized == null) {
            return Criteria.where(fieldName).ne(idValue);
        }
        return Criteria.where(fieldName).ne(normalized);
    }

    public static Criteria arrayContainsIdCriteria(String fieldName, String idValue) {
        String normalized = normalize(idValue);
        if (normalized == null) {
            return Criteria.where(fieldName).is(idValue);
        }
        return Criteria.where(fieldName).is(normalized);
    }

    public static String normalize(Object id) {
        if (id == null) {
            return null;
        }

        String raw = id.toString();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }
}