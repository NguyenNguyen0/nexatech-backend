package com.nexatech.news.service;

import com.nexatech.news.dto.request.NewsCreationRequest;
import com.nexatech.news.dto.request.NewsUpdateRequest;
import com.nexatech.news.dto.request.NewsVisibilityRequest;
import com.nexatech.news.dto.response.NewsListResponse;
import com.nexatech.news.dto.response.NewsResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface NewsService {
    List<NewsListResponse> getPublicNews();
    
    NewsResponse getPublicNewsBySlug(String slug);
    
    List<NewsListResponse> getAllNews();
    
    NewsResponse getNewsBySlug(String slug);
    
    NewsResponse createNews(NewsCreationRequest request, MultipartFile thumbnail);
    
    NewsResponse updateNews(String id, NewsUpdateRequest request, MultipartFile thumbnail);
    
    NewsResponse updateNewsVisibility(String id, NewsVisibilityRequest request);
    
    void deleteNews(String id);
}
