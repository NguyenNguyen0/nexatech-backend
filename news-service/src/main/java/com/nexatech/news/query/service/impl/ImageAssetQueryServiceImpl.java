package com.nexatech.news.query.service.impl;

import com.nexatech.news.command.aggregate.ImageAsset;
import com.nexatech.news.enums.ErrorCode;
import com.nexatech.news.exception.AppException;
import com.nexatech.news.query.model.ImageAssetResponse;
import com.nexatech.news.query.projection.ImageAssetMapper;
import com.nexatech.news.query.service.ImageAssetQueryService;
import com.nexatech.news.command.data.ImageAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAssetQueryServiceImpl implements ImageAssetQueryService {

    private final ImageAssetRepository imageAssetRepository;
    private final ImageAssetMapper imageAssetMapper;

    @Override
    @Transactional(readOnly = true)
    public ImageAssetResponse getNewsImage(String imageId) {
        log.info("Getting news image: imageId={}", imageId);

        ImageAsset imageAsset = imageAssetRepository.findById(imageId)
                .orElseThrow(() -> new AppException(ErrorCode.IMAGE_NOT_FOUND));

        return imageAssetMapper.toImageAssetResponse(imageAsset);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImageAssetResponse> getNewsImagesByOwnerId(String newsId) {
        log.info("Getting all images for news: newsId={}", newsId);

        List<ImageAsset> images = imageAssetRepository.findByNewsId(newsId);

        return images.stream()
                .map(imageAssetMapper::toImageAssetResponse)
                .collect(Collectors.toList());
    }
}
