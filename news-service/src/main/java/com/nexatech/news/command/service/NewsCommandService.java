package com.nexatech.news.command.service;

import com.nexatech.news.command.command.NewsCreationRequest;
import com.nexatech.news.command.command.NewsUpdateRequest;
import com.nexatech.news.command.command.NewsVisibilityRequest;
import com.nexatech.news.query.model.NewsResponse;
import org.springframework.web.multipart.MultipartFile;

public interface NewsCommandService {
    NewsResponse createNews(NewsCreationRequest request, MultipartFile thumbnail);

    NewsResponse updateNews(String id, NewsUpdateRequest request, MultipartFile thumbnail);

    NewsResponse updateNewsVisibility(String id, NewsVisibilityRequest request);

    void deleteNews(String id);
}
