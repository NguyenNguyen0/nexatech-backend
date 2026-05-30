package com.nexatech.user.service;

import com.nexatech.user.config.AwsS3Properties;
import com.nexatech.user.dto.response.AvatarUploadResponse;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;
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
public class AvatarStorageService {

    private static final long MAX_BYTES = 1024L * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private final S3Client s3Client;
    private final AwsS3Properties properties;

    public AvatarUploadResponse upload(MultipartFile file) {
        validateConfigured();
        validateFile(file);

        byte[] bytes = readBytes(file);
        String key = buildKey(file.getOriginalFilename());
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
            log.error("AWS S3 avatar upload failed [bucket={}, key={}]: {}",
                    properties.getBucket(), key, e.awsErrorDetails().errorMessage());
            throw new AppException(ErrorCode.AVATAR_UPLOAD_FAILED);
        }

        String url = buildPublicUrl(key);
        log.info("Uploaded avatar to AWS S3 [bucket={}, key={}, size={}]", properties.getBucket(), key, bytes.length);

        return AvatarUploadResponse.builder()
                .url(url)
                .key(key)
                .contentType(contentType)
                .size(bytes.length)
                .build();
    }

    private void validateConfigured() {
        if (!StringUtils.hasText(properties.getBucket())) {
            throw new AppException(ErrorCode.AVATAR_S3_NOT_CONFIGURED);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.AVATAR_FILE_INVALID);
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new AppException(ErrorCode.AVATAR_FILE_INVALID);
        }

        if (file.getSize() > MAX_BYTES) {
            throw new AppException(ErrorCode.AVATAR_FILE_TOO_LARGE);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new AppException(ErrorCode.AVATAR_FILE_INVALID);
        }
    }

    private String buildKey(String originalFilename) {
        String extension = ".jpg";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        return "avatars/" + UUID.randomUUID() + extension;
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
}
