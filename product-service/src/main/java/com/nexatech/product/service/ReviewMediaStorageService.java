package com.nexatech.product.service;

import com.nexatech.product.config.AwsS3Properties;
import com.nexatech.product.dto.response.ReviewMediaUploadResponse;
import com.nexatech.product.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewMediaStorageService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 25L * 1024 * 1024;

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> VIDEO_CONTENT_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime");

    private final S3Client s3Client;
    private final AwsS3Properties properties;

    public ReviewMediaUploadResponse upload(MultipartFile file) {
        validateConfigured();
        MediaKind kind = resolveMediaKind(file);
        validateSize(file, kind);

        byte[] bytes = readBytes(file);
        String key = buildKey(kind, file.getOriginalFilename());
        String contentType = file.getContentType();

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) bytes.length)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            log.error("AWS S3 review media upload failed [bucket={}, key={}]: {}",
                    properties.getBucket(), key, e.awsErrorDetails().errorMessage());
            throw new BadRequestException("Failed to upload media to storage");
        }

        String url = buildPublicUrl(key);
        log.info("Uploaded review media to AWS S3 [bucket={}, key={}, type={}, size={}]",
                properties.getBucket(), key, kind.value(), bytes.length);

        return ReviewMediaUploadResponse.builder()
                .url(url)
                .key(key)
                .mediaType(kind.value())
                .contentType(contentType)
                .size(bytes.length)
                .build();
    }

    private void validateConfigured() {
        if (!StringUtils.hasText(properties.getBucket())) {
            throw new BadRequestException("Chưa cấu hình AWS S3 bucket cho upload media đánh giá");
        }
    }

    private MediaKind resolveMediaKind(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Media file must not be empty");
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)) {
            throw new BadRequestException("Unsupported media type");
        }

        if (IMAGE_CONTENT_TYPES.contains(contentType)) {
            return MediaKind.IMAGE;
        }
        if (VIDEO_CONTENT_TYPES.contains(contentType)) {
            return MediaKind.VIDEO;
        }

        throw new BadRequestException("Unsupported media type. Allowed: JPEG, PNG, WebP, GIF, MP4, WebM, MOV");
    }

    private void validateSize(MultipartFile file, MediaKind kind) {
        long maxBytes = kind == MediaKind.VIDEO ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
        if (file.getSize() > maxBytes) {
            String limitLabel = kind == MediaKind.VIDEO ? "25MB" : "10MB";
            throw new BadRequestException("File exceeds maximum size of " + limitLabel);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new BadRequestException("Failed to read uploaded file");
        }
    }

    private String buildKey(MediaKind kind, String originalFilename) {
        String extension = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        return "reviews/" + kind.folder() + "/" + UUID.randomUUID() + extension;
    }

    private String buildPublicUrl(String key) {
        String encodedKey = encodeKey(key);
        return "https://" + properties.getBucket() + ".s3." + properties.getRegion() + ".amazonaws.com/" + encodedKey;
    }

    private String encodeKey(String key) {
        String[] parts = key.split("/");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20");
        }
        return String.join("/", parts);
    }

    private enum MediaKind {
        IMAGE("image", "images"),
        VIDEO("video", "videos");

        private final String value;
        private final String folder;

        MediaKind(String value, String folder) {
            this.value = value;
            this.folder = folder;
        }

        String value() {
            return value;
        }

        String folder() {
            return folder;
        }
    }
}
