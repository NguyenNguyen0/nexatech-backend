package com.nexatech.news.command.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.news.command.command.NewsCreationRequest;
import com.nexatech.news.command.command.NewsUpdateRequest;
import com.nexatech.news.command.command.NewsVisibilityRequest;
import com.nexatech.news.command.aggregate.News;
import com.nexatech.news.command.service.NewsCommandService;
import com.nexatech.news.enums.ErrorCode;
import com.nexatech.news.enums.NewsStatus;
import com.nexatech.news.exception.AppException;
import com.nexatech.news.query.model.NewsResponse;
import com.nexatech.news.query.projection.NewsMapper;
import com.nexatech.news.command.data.NewsRepository;
import com.nexatech.news.command.service.storage.MediaStorageService;
import com.nexatech.news.command.service.storage.StoredImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCommandServiceImpl implements NewsCommandService {

    private final NewsRepository newsRepository;
    private final NewsMapper newsMapper;
    private final MediaStorageService mediaStorageService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    @CacheEvict(value = "news", allEntries = true)
    public NewsResponse createNews(NewsCreationRequest request, MultipartFile thumbnail) {
        log.info("Creating news: title={}, slug={}, id={}, hasThumbnail={}",
                request.getTitle(), request.getSlug(), request.getId(), thumbnail != null);

        News news;

        if (thumbnail != null && !thumbnail.isEmpty()) {
            try {
                String folder = "news/thumbnails";
                StoredImage uploadedThumbnail = mediaStorageService.uploadImage(thumbnail, folder);
                request.setThumbnailUrl(uploadedThumbnail.url());
                log.info("Thumbnail uploaded successfully: {}", uploadedThumbnail.url());
            } catch (IOException e) {
                log.error("Failed to upload thumbnail", e);
                throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
            }
        }

        if (request.getId() != null && !request.getId().isEmpty()) {
            news = newsRepository.findById(request.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.NEWS_NOT_FOUND));

            if (!request.getSlug().equals(news.getSlug()) && newsRepository.existsBySlug(request.getSlug())) {
                throw new AppException(ErrorCode.NEWS_SLUG_EXISTED);
            }

            news.setTitle(request.getTitle());
            news.setSlug(request.getSlug());
            news.setDescription(request.getDescription());
            if (request.getThumbnailUrl() != null) {
                news.setThumbnailUrl(request.getThumbnailUrl());
            }
            news.setContentJson(parseContentJson(request.getContentJson()));
            news.setContentHtml(request.getContentHtml());
            if (request.getIsPublic() != null) {
                news.setIsPublic(request.getIsPublic());
            }

            log.info("Updating existing news with id={}", request.getId());
        } else {
            if (newsRepository.existsBySlug(request.getSlug())) {
                throw new AppException(ErrorCode.NEWS_SLUG_EXISTED);
            }

            news = newsMapper.toNews(request);
            news.setContentJson(parseContentJson(request.getContentJson()));
            news.setStatus(NewsStatus.DRAFT);

            log.info("Creating new news");
        }

        if (Boolean.TRUE.equals(news.getIsPublic())) {
            if (news.getPublishedAt() == null) {
                news.setPublishedAt(LocalDateTime.now());
            }
            news.setStatus(NewsStatus.PUBLISHED);
        }

        news = newsRepository.save(news);
        log.info("News saved successfully: id={}, slug={}", news.getId(), news.getSlug());

        return newsMapper.toNewsResponse(news);
    }

    @Override
    @Transactional
    @CacheEvict(value = "news", allEntries = true)
    public NewsResponse updateNews(String id, NewsUpdateRequest request, MultipartFile thumbnail) {
        log.info("Updating news: id={}, hasThumbnail={}", id, thumbnail != null);

        News news = newsRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NEWS_NOT_FOUND));

        if (thumbnail != null && !thumbnail.isEmpty()) {
            try {
                String folder = "news/thumbnails";
                StoredImage uploadedThumbnail = mediaStorageService.uploadImage(thumbnail, folder);
                request.setThumbnailUrl(uploadedThumbnail.url());
                log.info("Thumbnail uploaded successfully: {}", uploadedThumbnail.url());
            } catch (IOException e) {
                log.error("Failed to upload thumbnail", e);
                throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
            }
        }

        if (request.getSlug() != null && !request.getSlug().equals(news.getSlug())) {
            if (newsRepository.existsBySlug(request.getSlug())) {
                throw new AppException(ErrorCode.NEWS_SLUG_EXISTED);
            }
        }

        newsMapper.updateNewsFromRequest(request, news);
        if (request.getContentJson() != null) {
            news.setContentJson(parseContentJson(request.getContentJson()));
        }

        if (request.getIsPublic() != null && request.getIsPublic()
                && news.getPublishedAt() == null) {
            news.setPublishedAt(LocalDateTime.now());
            news.setStatus(NewsStatus.PUBLISHED);
        }

        news = newsRepository.save(news);
        log.info("News updated successfully: id={}", id);

        return newsMapper.toNewsResponse(news);
    }

    @Override
    @Transactional
    @CacheEvict(value = "news", allEntries = true)
    public NewsResponse updateNewsVisibility(String id, NewsVisibilityRequest request) {
        log.info("Updating news visibility: id={}, isPublic={}", id, request.getIsPublic());

        News news = newsRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NEWS_NOT_FOUND));

        news.setIsPublic(request.getIsPublic());

        if (Boolean.TRUE.equals(request.getIsPublic())) {
            if (news.getPublishedAt() == null) {
                news.setPublishedAt(LocalDateTime.now());
            }
            news.setStatus(NewsStatus.PUBLISHED);
        } else {
            news.setStatus(NewsStatus.DRAFT);
        }

        news = newsRepository.save(news);
        log.info("News visibility updated successfully: id={}, isPublic={}", id, request.getIsPublic());

        return newsMapper.toNewsResponse(news);
    }

    @Override
    @Transactional
    @CacheEvict(value = "news", allEntries = true)
    public void deleteNews(String id) {
        log.info("Deleting news: id={}", id);

        News news = newsRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NEWS_NOT_FOUND));

        newsRepository.delete(news);
        log.info("News deleted successfully: id={}", id);
    }

    private Map<String, Object> parseContentJson(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(contentJson, new TypeReference<>() {});
        } catch (IOException e) {
            log.warn("Invalid contentJson payload", e);
            throw new AppException(ErrorCode.INVALID_KEY);
        }
    }
}
