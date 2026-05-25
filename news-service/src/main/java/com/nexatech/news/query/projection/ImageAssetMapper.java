package com.nexatech.news.query.projection;

import com.nexatech.news.query.model.ImageAssetResponse;
import com.nexatech.news.command.aggregate.ImageAsset;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ImageAssetMapper {
    ImageAssetResponse toImageAssetResponse(ImageAsset imageAsset);
}
