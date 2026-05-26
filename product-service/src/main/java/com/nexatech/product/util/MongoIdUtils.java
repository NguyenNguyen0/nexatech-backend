package com.nexatech.product.util;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.StringUtils;

import java.util.Locale;

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
        // Query as both String and ObjectId to handle IDs stored in either format.
        if (ObjectId.isValid(normalized)) {
            return new Criteria().orOperator(
                    Criteria.where(fieldName).is(normalized),
                    Criteria.where(fieldName).is(new ObjectId(normalized))
            );
        }
        return Criteria.where(fieldName).is(normalized);
    }

    public static Criteria idNotEqualsCriteria(String fieldName, String idValue) {
        String normalized = normalize(idValue);
        if (normalized == null) {
            return Criteria.where(fieldName).ne(idValue);
        }
        // Exclude both String and ObjectId representations.
        if (ObjectId.isValid(normalized)) {
            return new Criteria().andOperator(
                    Criteria.where(fieldName).ne(normalized),
                    Criteria.where(fieldName).ne(new ObjectId(normalized))
            );
        }
        return Criteria.where(fieldName).ne(normalized);
    }

    public static Criteria arrayContainsIdCriteria(String fieldName, String idValue) {
        String normalized = normalize(idValue);
        if (normalized == null) {
            return Criteria.where(fieldName).is(idValue);
        }
        // Always query as string value directly, don't convert to ObjectId.
        // Array field may contain mixed string/ObjectId, so include both variants in query.
        if (ObjectId.isValid(normalized)) {
            return new Criteria().orOperator(
                    Criteria.where(fieldName).is(normalized),
                    Criteria.where(fieldName).is(new ObjectId(normalized))
            );
        }
        return Criteria.where(fieldName).is(normalized);
    }

    public static String normalize(Object id) {
        if (id == null) {
            return null;
        }
        if (id instanceof ObjectId objectId) {
            return objectId.toHexString();
        }

        String raw = id.toString();
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        String value = raw.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("objectid(\"") && value.endsWith("\")")) {
            value = value.substring(10, value.length() - 2);
        } else if (lower.startsWith("objectid('") && value.endsWith("')")) {
            value = value.substring(10, value.length() - 2);
        }

        return ObjectId.isValid(value) ? value.toLowerCase(Locale.ROOT) : value;
    }
}