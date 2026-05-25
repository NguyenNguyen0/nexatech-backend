package com.nexatech.news.query.service;

import com.nexatech.news.query.model.ImageAssetResponse;

import java.util.List;

public interface ImageAssetQueryService {
    ImageAssetResponse getNewsImage(String imageId);

    List<ImageAssetResponse> getNewsImagesByOwnerId(String newsId);
}
