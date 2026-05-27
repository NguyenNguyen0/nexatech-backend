package com.nexatech.news.query.projection;

import com.nexatech.news.command.command.NewsCreationRequest;
import com.nexatech.news.command.command.NewsUpdateRequest;
import com.nexatech.news.query.model.NewsListResponse;
import com.nexatech.news.query.model.NewsResponse;
import com.nexatech.news.command.aggregate.News;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = {ImageAssetMapper.class})
public interface NewsMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "contentJson", ignore = true)
    News toNews(NewsCreationRequest request);
    
    NewsResponse toNewsResponse(News news);
    
    NewsListResponse toNewsListResponse(News news);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "contentJson", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateNewsFromRequest(NewsUpdateRequest request, @MappingTarget News news);
}
