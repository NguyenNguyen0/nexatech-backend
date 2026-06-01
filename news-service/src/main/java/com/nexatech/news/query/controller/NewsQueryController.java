package com.nexatech.news.query.controller;

import com.nexatech.news.constant.PermissionsAccept;
import com.nexatech.news.query.model.ApiResponse;
import com.nexatech.news.query.model.ImageAssetResponse;
import com.nexatech.news.query.model.NewsListResponse;
import com.nexatech.news.query.model.NewsResponse;
import com.nexatech.news.query.service.ImageAssetQueryService;
import com.nexatech.news.query.service.NewsQueryService;
import com.nexatech.news.security.RequiresPermission;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NewsQueryController {

    NewsQueryService newsQueryService;
    ImageAssetQueryService imageAssetQueryService;

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<List<NewsListResponse>>> getPublicNews() {
        log.info("Request to get all public news");

        List<NewsListResponse> newsList = newsQueryService.getPublicNews();

        return ResponseEntity.ok(
                ApiResponse.<List<NewsListResponse>>builder()
                        .message("Public news retrieved successfully")
                        .result(newsList)
                        .build()
        );
    }

    @GetMapping("/public/{slug}")
    public ResponseEntity<ApiResponse<NewsResponse>> getPublicNewsBySlug(@PathVariable String slug) {
        log.info("Request to get public news by slug: {}", slug);

        NewsResponse news = newsQueryService.getPublicNewsBySlug(slug);

        return ResponseEntity.ok(
                ApiResponse.<NewsResponse>builder()
                        .message("News retrieved successfully")
                        .result(news)
                        .build()
        );
    }

    @GetMapping
    @RequiresPermission(PermissionsAccept.News.READ)
    public ResponseEntity<ApiResponse<List<NewsListResponse>>> getAllNews() {
        log.info("Request to get all news (admin)");

        List<NewsListResponse> newsList = newsQueryService.getAllNews();

        return ResponseEntity.ok(
                ApiResponse.<List<NewsListResponse>>builder()
                        .message("All news retrieved successfully")
                        .result(newsList)
                        .build()
        );
    }

    @GetMapping("/{slug}")
    @RequiresPermission(PermissionsAccept.News.READ)
    public ResponseEntity<ApiResponse<NewsResponse>> getNewsBySlug(@PathVariable String slug) {
        log.info("Request to get news by slug (admin): {}", slug);

        NewsResponse news = newsQueryService.getNewsBySlug(slug);

        return ResponseEntity.ok(
                ApiResponse.<NewsResponse>builder()
                        .message("News retrieved successfully")
                        .result(news)
                        .build()
        );
    }

    @GetMapping("/{newsId}/images")
    @RequiresPermission(PermissionsAccept.News.READ)
    public ResponseEntity<ApiResponse<List<ImageAssetResponse>>> getNewsImages(@PathVariable String newsId) {
        log.info("Request to get all images for news: newsId={}", newsId);

        List<ImageAssetResponse> images = imageAssetQueryService.getNewsImagesByOwnerId(newsId);

        return ResponseEntity.ok(
                ApiResponse.<List<ImageAssetResponse>>builder()
                        .message("News images retrieved successfully")
                        .result(images)
                        .build()
        );
    }

    @GetMapping("/images/{imageId}")
    @RequiresPermission(PermissionsAccept.News.READ)
    public ResponseEntity<ApiResponse<ImageAssetResponse>> getNewsImage(@PathVariable String imageId) {
        log.info("Request to get news image: imageId={}", imageId);

        ImageAssetResponse image = imageAssetQueryService.getNewsImage(imageId);

        return ResponseEntity.ok(
                ApiResponse.<ImageAssetResponse>builder()
                        .message("Image retrieved successfully")
                        .result(image)
                        .build()
        );
    }
}
