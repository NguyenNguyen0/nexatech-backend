package com.nexatech.news.query.service.impl;

import com.nexatech.news.command.aggregate.News;
import com.nexatech.news.query.model.NewsListResponse;
import com.nexatech.news.query.model.NewsResponse;
import com.nexatech.news.enums.ErrorCode;
import com.nexatech.news.enums.NewsStatus;
import com.nexatech.news.exception.AppException;
import com.nexatech.news.query.projection.NewsMapper;
import com.nexatech.news.query.service.NewsQueryService;
import com.nexatech.news.command.data.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsQueryServiceImpl implements NewsQueryService {

    private final NewsRepository newsRepository;
    private final NewsMapper newsMapper;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "news", key = "'public_list'")
    public List<NewsListResponse> getPublicNews() {
        log.info("Getting all public news");

        List<News> newsList = newsRepository.findByIsPublicTrueAndStatus(NewsStatus.PUBLISHED);

        return newsList.stream()
                .map(newsMapper::toNewsListResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "news", key = "'public_detail:' + #slug")
    public NewsResponse getPublicNewsBySlug(String slug) {
        log.info("Getting public news by slug: {}", slug);

        News news = newsRepository.findBySlugAndIsPublicTrueAndStatus(slug, NewsStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(ErrorCode.NEWS_NOT_FOUND));

        return newsMapper.toNewsResponse(news);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NewsListResponse> getAllNews() {
        log.info("Getting all news (admin)");

        List<News> newsList = newsRepository.findAll();

        return newsList.stream()
                .map(newsMapper::toNewsListResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public NewsResponse getNewsBySlug(String slug) {
        log.info("Getting news by slug (admin): {}", slug);

        News news = newsRepository.findBySlug(slug)
                .orElseThrow(() -> new AppException(ErrorCode.NEWS_NOT_FOUND));

        return newsMapper.toNewsResponse(news);
    }
}
