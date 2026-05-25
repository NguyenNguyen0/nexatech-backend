package com.nexatech.news.command.controller;

import com.nexatech.news.command.command.NewsCreationRequest;
import com.nexatech.news.command.command.NewsUpdateRequest;
import com.nexatech.news.command.command.NewsVisibilityRequest;
import com.nexatech.news.command.service.ImageAssetCommandService;
import com.nexatech.news.command.service.NewsCommandService;
import com.nexatech.news.query.model.ApiResponse;
import com.nexatech.news.query.model.ImageAssetResponse;
import com.nexatech.news.query.model.NewsResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NewsCommandController {

    NewsCommandService newsCommandService;
    ImageAssetCommandService imageAssetCommandService;

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<ApiResponse<NewsResponse>> createNews(
            @Valid @ModelAttribute NewsCreationRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) {
        log.info("Request to create news: title={}, hasThumbnail={}",
                request.getTitle(), thumbnail != null);

        NewsResponse news = newsCommandService.createNews(request, thumbnail);

        return ResponseEntity.ok(
                ApiResponse.<NewsResponse>builder()
                        .message("News created successfully")
                        .result(news)
                        .build()
        );
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<ApiResponse<NewsResponse>> updateNews(
            @PathVariable String id,
            @Valid @ModelAttribute NewsUpdateRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) {
        log.info("Request to update news: id={}, hasThumbnail={}", id, thumbnail != null);

        NewsResponse news = newsCommandService.updateNews(id, request, thumbnail);

        return ResponseEntity.ok(
                ApiResponse.<NewsResponse>builder()
                        .message("News updated successfully")
                        .result(news)
                        .build()
        );
    }

    @PatchMapping("/{id}/visibility")
    public ResponseEntity<ApiResponse<NewsResponse>> updateNewsVisibility(
            @PathVariable String id,
            @Valid @RequestBody NewsVisibilityRequest request) {
        log.info("Request to update news visibility: id={}, isPublic={}", id, request.getIsPublic());

        NewsResponse news = newsCommandService.updateNewsVisibility(id, request);

        return ResponseEntity.ok(
                ApiResponse.<NewsResponse>builder()
                        .message("News visibility updated successfully")
                        .result(news)
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNews(@PathVariable String id) {
        log.info("Request to delete news: id={}", id);

        newsCommandService.deleteNews(id);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("News deleted successfully")
                        .build()
        );
    }

    @PostMapping("/{newsId}/images")
    public ResponseEntity<ApiResponse<ImageAssetResponse>> uploadNewsImage(
            @PathVariable String newsId,
            @RequestPart("file") MultipartFile file) {
        log.info("Request to upload image for news: newsId={}, filename={}", newsId, file.getOriginalFilename());

        Long uploadedBy = null; // TODO: Extract user ID from authentication

        ImageAssetResponse imageAsset = imageAssetCommandService.uploadNewsImage(file, newsId, uploadedBy);

        return ResponseEntity.ok(
                ApiResponse.<ImageAssetResponse>builder()
                        .message("Image uploaded successfully")
                        .result(imageAsset)
                        .build()
        );
    }

    @DeleteMapping("/{newsId}/images")
    public ResponseEntity<ApiResponse<Void>> deleteAllNewsImages(@PathVariable String newsId) {
        log.info("Request to delete all images for news: newsId={}", newsId);

        imageAssetCommandService.deleteAllImagesByOwnerId(newsId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("All images deleted successfully")
                        .build()
        );
    }

    @DeleteMapping("/images/public/{publicId}")
    public ResponseEntity<ApiResponse<Void>> deleteImageByPublicId(@PathVariable String publicId) {
        log.info("Request to delete image by publicId: {}", publicId);

        imageAssetCommandService.deleteImageByPublicId(publicId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Image deleted successfully")
                        .build()
        );
    }
}
