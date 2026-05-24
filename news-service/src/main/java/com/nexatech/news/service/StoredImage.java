package com.nexatech.news.service;

public record StoredImage(
        String key,
        String url,
        Integer width,
        Integer height,
        Long sizeBytes,
        String contentType) {
}
