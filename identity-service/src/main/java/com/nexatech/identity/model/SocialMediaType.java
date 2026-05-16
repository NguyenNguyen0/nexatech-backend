package com.nexatech.identity.model;

import com.nexatech.identity.exception.AppException;
import com.nexatech.identity.exception.ErrorCode;

public enum SocialMediaType {
    GOOGLE;

    public static SocialMediaType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }
        try {
            return SocialMediaType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        }
    }
}
