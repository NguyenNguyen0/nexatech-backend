package com.nexatech.news.command.aggregate;

import com.nexatech.news.enums.ImageStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "image_assets")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImageAsset extends BaseEntity {

    @Column(name = "public_id", nullable = false)
    String publicId; // AWS S3 object key

    @Column(nullable = false)
    String url; // Public S3 or CDN URL

    Integer width;

    Integer height;

    @Column(name = "size_bytes")
    Long sizeBytes;

    @Column(name = "mime_type")
    String mimeType;

    @Column(name = "alt_text")
    String altText;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    News news;

    @Column(name = "owner_type")
    String ownerType; // e.g. 'news'

    @Column(name = "usage_path")
    String usagePath; // where in content this image is used

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    ImageStatus status = ImageStatus.TEMP;
}
