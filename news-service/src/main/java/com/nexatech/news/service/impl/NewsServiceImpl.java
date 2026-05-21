package com.nexatech.news.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.news.dto.request.NewsCreationRequest;
import com.nexatech.news.dto.request.NewsUpdateRequest;
import com.nexatech.news.dto.request.NewsVisibilityRequest;
import com.nexatech.news.dto.response.NewsListResponse;
import com.nexatech.news.dto.response.NewsResponse;
import com.nexatech.news.entity.News;
import com.nexatech.news.enums.ErrorCode;
import com.nexatech.news.enums.NewsStatus;
import com.nexatech.news.exception.AppException;
import com.nexatech.news.mapper.NewsMapper;
import com.nexatech.news.repository.NewsRepository;
import com.nexatech.news.service.MediaStorageService;
import com.nexatech.news.service.NewsService;
import com.nexatech.news.service.StoredImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsServiceImpl implements NewsService {

    private final NewsRepository newsRepository;
    private final NewsMapper newsMapper;
    private final MediaStorageService mediaStorageService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "news", key = "'public_list'")
    public List<NewsListResponse> getPublicNews() {
        log.info("Getting all public news");

        List<News> newsList = newsRepository.findByIsPublicTrueAndStatus(NewsStatus.PUBLISHED);

        return newsList.stream()
                .map(newsMapper::toNewsListResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "news", key = "'public_detail:' + #slug")
    public NewsResponse getPublicNewsBySlug(String slug) {
        log.info("Getting public news by slug: {}", slug);

        News news = newsRepository.findBySlugAndIsPublicTrueAndStatus(slug, NewsStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(ErrorCode.NEWS_NOT_FOUND));

        return newsMapper.toNewsResponse(news);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NewsListResponse> getAllNews() {
        log.info("Getting all news (admin)");

        List<News> newsList = newsRepository.findAll();

        return newsList.stream()
                .map(newsMapper::toNewsListResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public NewsResponse getNewsBySlug(String slug) {
        log.info("Getting news by slug (admin): {}", slug);

        News news = newsRepository.findBySlug(slug)
                .orElseThrow(() -> new AppException(ErrorCode.NEWS_NOT_FOUND));

        return newsMapper.toNewsResponse(news);
    }

    @Override
    @Transactional
    @CacheEvict(value = "news", allEntries = true)
    public NewsResponse createNews(NewsCreationRequest request, MultipartFile thumbnail) {
        log.info("Creating news: title={}, slug={}, id={}, hasThumbnail={}", 
                request.getTitle(), request.getSlug(), request.getId(), thumbnail != null);

        News news;
        
        // Upload thumbnail if provided
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
        
        // If ID is provided, update existing news instead of creating new one
        if (request.getId() != null && !request.getId().isEmpty()) {
            news = newsRepository.findById(request.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.NEWS_NOT_FOUND));
            
            // Check slug uniqueness if slug is being changed
            if (!request.getSlug().equals(news.getSlug()) && newsRepository.existsBySlug(request.getSlug())) {
                throw new AppException(ErrorCode.NEWS_SLUG_EXISTED);
            }
            
            // Update existing news
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
            // Check if slug already exists
            if (newsRepository.existsBySlug(request.getSlug())) {
                throw new AppException(ErrorCode.NEWS_SLUG_EXISTED);
            }

            news = newsMapper.toNews(request);
            news.setContentJson(parseContentJson(request.getContentJson()));
            news.setStatus(NewsStatus.DRAFT);
            
            log.info("Creating new news");
        }
        
        // Set publishedAt if isPublic is true
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

        // Upload thumbnail if provided
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

        // Check slug uniqueness if slug is being updated
        if (request.getSlug() != null && !request.getSlug().equals(news.getSlug())) {
            if (newsRepository.existsBySlug(request.getSlug())) {
                throw new AppException(ErrorCode.NEWS_SLUG_EXISTED);
            }
        }

        newsMapper.updateNewsFromRequest(request, news);
        if (request.getContentJson() != null) {
            news.setContentJson(parseContentJson(request.getContentJson()));
        }
        
        // Update publishedAt if isPublic is being set to true and not yet published
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

        // Update status and publishedAt based on visibility
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
