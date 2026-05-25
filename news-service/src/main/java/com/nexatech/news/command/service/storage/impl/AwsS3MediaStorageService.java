package com.nexatech.news.command.service.storage.impl;

import com.nexatech.news.command.service.storage.MediaStorageService;
import com.nexatech.news.command.service.storage.StoredImage;
import com.nexatech.news.config.AwsS3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsS3MediaStorageService implements MediaStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private final S3Client s3Client;
    private final AwsS3Properties properties;

    @Override
    public StoredImage uploadImage(MultipartFile file, String folder) throws IOException {
        validateImage(file);

        byte[] bytes = file.getBytes();
        String key = buildKey(folder, file.getOriginalFilename());
        String contentType = file.getContentType();
        ImageDimensions dimensions = readDimensions(bytes);

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
            log.error("AWS S3 upload failed [bucket={}, key={}]: {}",
                    properties.getBucket(), key, e.awsErrorDetails().errorMessage());
            throw new IOException("Failed to upload image to AWS S3", e);
        }

        String url = buildPublicUrl(key);
        log.info("Uploaded image to AWS S3 [bucket={}, key={}, size={}]", properties.getBucket(), key, bytes.length);
        return new StoredImage(key, url, dimensions.width(), dimensions.height(), (long) bytes.length, contentType);
    }

    @Override
    public void deleteImage(String key) throws IOException {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
            log.info("Deleted image from AWS S3 [bucket={}, key={}]", properties.getBucket(), key);
        } catch (S3Exception e) {
            log.error("AWS S3 delete failed [bucket={}, key={}]: {}",
                    properties.getBucket(), key, e.awsErrorDetails().errorMessage());
            throw new IOException("Failed to delete image from AWS S3", e);
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image must not be empty");
        }
        if (!StringUtils.hasText(properties.getBucket())) {
            throw new IllegalStateException("AWS S3 bucket is not configured");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported image type");
        }
    }

    private ImageDimensions readDimensions(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return new ImageDimensions(null, null);
            }
            return new ImageDimensions(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            log.warn("Failed to read image dimensions", e);
            return new ImageDimensions(null, null);
        }
    }

    private String buildKey(String folder, String originalFilename) {
        String normalizedFolder = StringUtils.hasText(folder)
                ? folder.replace("\\", "/").replaceAll("^/+", "").replaceAll("/+$", "")
                : "news";
        String extension = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        return normalizedFolder + "/" + UUID.randomUUID() + extension;
    }

    private String buildPublicUrl(String key) {
        String encodedKey = encodeKey(key);
        if (StringUtils.hasText(properties.getPublicUrl())) {
            return properties.getPublicUrl().replaceAll("/+$", "") + "/" + encodedKey;
        }

        return "https://" + properties.getBucket() + ".s3." + properties.getRegion() + ".amazonaws.com/" + encodedKey;
    }

    private String encodeKey(String key) {
        String[] parts = key.split("/");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20");
        }
        return String.join("/", parts);
    }

    private record ImageDimensions(Integer width, Integer height) {
    }
}
