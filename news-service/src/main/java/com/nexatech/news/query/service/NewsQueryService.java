package com.nexatech.news.query.service;

import com.nexatech.news.query.model.NewsListResponse;
import com.nexatech.news.query.model.NewsResponse;

import java.util.List;

public interface NewsQueryService {
    List<NewsListResponse> getPublicNews();

    NewsResponse getPublicNewsBySlug(String slug);

    List<NewsListResponse> getAllNews();

    NewsResponse getNewsBySlug(String slug);
}
