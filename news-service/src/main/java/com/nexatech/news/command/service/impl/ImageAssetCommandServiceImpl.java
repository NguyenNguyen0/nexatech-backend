package com.nexatech.news.command.service.impl;

import com.nexatech.news.command.aggregate.ImageAsset;
import com.nexatech.news.command.aggregate.News;
import com.nexatech.news.command.service.ImageAssetCommandService;
import com.nexatech.news.enums.ErrorCode;
import com.nexatech.news.enums.ImageStatus;
import com.nexatech.news.exception.AppException;
import com.nexatech.news.query.model.ImageAssetResponse;
import com.nexatech.news.query.projection.ImageAssetMapper;
import com.nexatech.news.command.data.ImageAssetRepository;
import com.nexatech.news.command.data.NewsRepository;
import com.nexatech.news.command.service.storage.MediaStorageService;
import com.nexatech.news.command.service.storage.StoredImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAssetCommandServiceImpl implements ImageAssetCommandService {

    private final ImageAssetRepository imageAssetRepository;
    private final NewsRepository newsRepository;
    private final MediaStorageService mediaStorageService;
    private final ImageAssetMapper imageAssetMapper;

    @Override
    @Transactional
    public ImageAssetResponse uploadNewsImage(MultipartFile file, String newsId, Long uploadedBy) {
        log.info("Uploading image for news: newsId={}, uploadedBy={}, filename={}",
                newsId, uploadedBy, file.getOriginalFilename());

        News news = newsRepository.findById(newsId)
                .orElseThrow(() -> new AppException(ErrorCode.NEWS_NOT_FOUND));

        try {
            String folder = "news/" + newsId;
            StoredImage uploadedImage = mediaStorageService.uploadImage(file, folder);

            ImageAsset imageAsset = ImageAsset.builder()
                    .publicId(uploadedImage.key())
                    .url(uploadedImage.url())
                    .width(uploadedImage.width())
                    .height(uploadedImage.height())
                    .sizeBytes(uploadedImage.sizeBytes())
                    .mimeType(uploadedImage.contentType())
                    .ownerType("news")
                    .status(ImageStatus.ATTACHED)
                    .news(news)
                    .build();

            imageAsset = imageAssetRepository.save(imageAsset);
            log.info("Image uploaded successfully: imageId={}, publicId={}",
                    imageAsset.getId(), imageAsset.getPublicId());

            return imageAssetMapper.toImageAssetResponse(imageAsset);

        } catch (IOException e) {
            log.error("Failed to upload image for news: newsId={}", newsId, e);
            throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    @Override
    @Transactional
    public void deleteAllImagesByOwnerId(String newsId) {
        log.info("Deleting all images for news: newsId={}", newsId);

        List<ImageAsset> images = imageAssetRepository.findByNewsId(newsId);

        for (ImageAsset image : images) {
            try {
                mediaStorageService.deleteImage(image.getPublicId());
                log.info("Deleted image from AWS S3: publicId={}", image.getPublicId());
            } catch (IOException e) {
                log.error("Failed to delete image from AWS S3: publicId={}",
                        image.getPublicId(), e);
            }
        }

        imageAssetRepository.deleteByNewsId(newsId);
        log.info("Deleted all images for news: newsId={}, count={}", newsId, images.size());
    }

    @Override
    @Transactional
    public void deleteImageByPublicId(String publicId) {
        log.info("Deleting image by publicId: {}", publicId);

        ImageAsset imageAsset = imageAssetRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AppException(ErrorCode.IMAGE_NOT_FOUND));

        try {
            mediaStorageService.deleteImage(publicId);
            imageAssetRepository.delete(imageAsset);
            log.info("Image deleted successfully: publicId={}", publicId);
        } catch (IOException e) {
            log.error("Failed to delete image: publicId={}", publicId, e);
            throw new AppException(ErrorCode.IMAGE_DELETE_FAILED);
        }
    }
}
