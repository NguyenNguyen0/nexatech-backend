package com.nexatech.news.command.service.storage;

public record StoredImage(
        String key,
        String url,
        Integer width,
        Integer height,
        Long sizeBytes,
        String contentType) {
}
