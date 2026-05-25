package com.nexatech.news.query.model;

import com.nexatech.news.enums.NewsStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NewsListResponse {
    String id;
    String slug;
    String title;
    String description;
    String thumbnailUrl;
    Boolean isPublic;
    NewsStatus status;
    LocalDateTime publishedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    String createdBy;
}
