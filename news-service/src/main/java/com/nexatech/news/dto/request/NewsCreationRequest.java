package com.nexatech.news.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NewsCreationRequest {
    String id; // Optional: for updating existing news
    
    @NotBlank(message = "NEWS_TITLE_REQUIRED")
    String title;
    
    @NotBlank(message = "NEWS_SLUG_REQUIRED")
    String slug;
    
    String description;
    
    // thumbnailUrl will be set by service after uploading file
    String thumbnailUrl;
    
    // Multipart form data sends JSON as a string; service parses it before saving.
    String contentJson;
    
    String contentHtml;
    
    Boolean isPublic;
    
}
