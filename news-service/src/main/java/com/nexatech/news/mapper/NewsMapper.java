package com.nexatech.news.mapper;

import com.nexatech.news.dto.request.NewsCreationRequest;
import com.nexatech.news.dto.request.NewsUpdateRequest;
import com.nexatech.news.dto.response.NewsListResponse;
import com.nexatech.news.dto.response.NewsResponse;
import com.nexatech.news.entity.News;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {ImageAssetMapper.class})
public interface NewsMapper {
    @Mapping(target = "id", ignore = true)
    News toNews(NewsCreationRequest request);
    
    NewsResponse toNewsResponse(News news);
    
    NewsListResponse toNewsListResponse(News news);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "images", ignore = true)
    void updateNewsFromRequest(NewsUpdateRequest request, @MappingTarget News news);
}
