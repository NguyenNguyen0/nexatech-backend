package com.nexatech.news.mapper;

import com.nexatech.news.dto.response.ImageAssetResponse;
import com.nexatech.news.entity.ImageAsset;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ImageAssetMapper {
    ImageAssetResponse toImageAssetResponse(ImageAsset imageAsset);
}
