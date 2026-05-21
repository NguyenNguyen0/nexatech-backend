package com.nexatech.news.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NewsUpdateRequest {
    String title;
    String slug;
    String description;
    String thumbnailUrl;
    
    // Multipart form data sends JSON as a string; service parses it before saving.
    String contentJson;
    
    String contentHtml;
    Boolean isPublic;
    
}
