package com.nexatech.news.query.model;

import com.nexatech.news.enums.ImageStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImageAssetResponse {
    String id;
    String publicId;
    String url;
    Integer width;
    Integer height;
    Long sizeBytes;
    String mimeType;
    String altText;
    String ownerType;
    String usagePath;
    ImageStatus status;
    Long uploadedBy;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
