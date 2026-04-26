package com.nexatech.storage.service;

import com.nexatech.storage.config.MinioProperties;
import com.nexatech.storage.dto.response.FileUploadResponse;
import com.nexatech.storage.exception.BadRequestException;
import com.nexatech.storage.exception.ResourceNotFoundException;
import com.nexatech.storage.exception.StorageException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB

    private final S3Client s3Client;
    private final MinioProperties minioProperties;

    /**
     * Uploads a file to the given bucket (defaults to configured bucket).
     * Called internally by product-service via Feign.
     */
    public FileUploadResponse uploadFile(MultipartFile file, String bucket) {
        validateFile(file);

        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : minioProperties.getBucket();
        String key = generateKey(file.getOriginalFilename());

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(targetBucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException e) {
            throw new StorageException("Failed to read uploaded file: " + e.getMessage());
        } catch (S3Exception e) {
            log.error("MinIO upload error [bucket={}, key={}]: {}", targetBucket, key,
                    e.awsErrorDetails().errorMessage());
            throw new StorageException("Failed to upload file to storage");
        }

        String url = minioProperties.getPublicUrl() + "/api/v1/files/" + targetBucket + "/" + key;
        log.info("Uploaded file [bucket={}, key={}, size={}]", targetBucket, key, file.getSize());

        return FileUploadResponse.builder()
                .url(url)
                .bucket(targetBucket)
                .key(key)
                .size(file.getSize())
                .contentType(file.getContentType())
                .build();
    }

    /**
     * Deletes a file from storage.
     * Called internally by product-service via Feign.
     */
    public void deleteFile(String bucket, String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.info("Deleted file [bucket={}, key={}]", bucket, key);
        } catch (S3Exception e) {
            log.error("MinIO delete error [bucket={}, key={}]: {}", bucket, key,
                    e.awsErrorDetails().errorMessage());
            throw new StorageException("Failed to delete file from storage");
        }
    }

    /**
     * Streams a file directly to the HTTP response.
     * Used by the public GET endpoint — avoids presigned URL host mismatch issues.
     */
    public void streamFile(String bucket, String key, HttpServletResponse response) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(request)) {
            GetObjectResponse metadata = s3Object.response();
            response.setContentType(metadata.contentType());
            if (metadata.contentLength() != null) {
                response.setContentLengthLong(metadata.contentLength());
            }
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
            s3Object.transferTo(response.getOutputStream());
        } catch (NoSuchKeyException e) {
            throw new ResourceNotFoundException("File not found: " + key);
        } catch (S3Exception e) {
            log.error("MinIO stream error [bucket={}, key={}]: {}", bucket, key,
                    e.awsErrorDetails().errorMessage());
            throw new StorageException("Failed to retrieve file from storage");
        } catch (IOException e) {
            throw new StorageException("Failed to stream file: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException("File size exceeds the 5 MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException(
                    "Unsupported file type. Allowed: image/jpeg, image/png, image/webp, image/gif");
        }
    }

    private String generateKey(String originalFilename) {
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        return UUID.randomUUID() + ext;
    }
}
