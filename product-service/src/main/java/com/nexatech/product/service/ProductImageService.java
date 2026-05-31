package com.nexatech.product.service;

import com.nexatech.product.config.AwsS3Properties;
import com.nexatech.product.dto.response.ProductResponse;
import com.nexatech.product.exception.BadRequestException;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ProductMapper;
import com.nexatech.product.model.Product;
import com.nexatech.product.repository.ProductRepository;
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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private static final int MAX_IMAGES = 10;
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final ProductRepository productRepository;
    private final S3Client s3Client;
    private final AwsS3Properties s3Properties;
    private final ProductMapper productMapper;
    private final ProductResponseEnricher productResponseEnricher;
    private final ProductCacheInvalidationService productCacheInvalidationService;

    /**
     * Uploads multiple images and appends their URLs to the product.
     * At most 10 images total per product.
     */
    public ProductResponse uploadImages(String productId, List<MultipartFile> files) {
        Product product = getProduct(productId);

        List<String> current = product.getImages() == null ? new ArrayList<>() : new ArrayList<>(product.getImages());

        if (current.size() + files.size() > MAX_IMAGES) {
            throw new BadRequestException(
                    "A product can have at most " + MAX_IMAGES + " images. " +
                            "Currently has " + current.size() + ", trying to add " + files.size() + ".");
        }

        for (MultipartFile file : files) {
            String imageUrl = uploadImageToS3(productId, file);
            current.add(imageUrl);
            log.info("Uploaded image for product [{}]: {}", productId, imageUrl);
        }

        product.setImages(current);
        if (!StringUtils.hasText(product.getThumbnail()) && !current.isEmpty()) {
            product.setThumbnail(current.getFirst());
        }
        Product saved = productRepository.save(product);
        productCacheInvalidationService.evictProductCaches(productId, "PRODUCT_IMAGES_UPDATED");
        return productResponseEnricher.enrichImageResponse(productMapper.toResponse(saved));
    }

    /**
     * Removes one image URL from the product and deletes the file from storage.
     */
    public ProductResponse deleteImage(String productId, String imageUrl) {
        Product product = getProduct(productId);

        List<String> current = product.getImages() == null ? List.of() : product.getImages();
        if (!current.contains(imageUrl)) {
            throw new BadRequestException("Image URL not found on this product: " + imageUrl);
        }

        String[] parts = extractBucketAndKey(imageUrl);
        deleteImageFromS3(parts[0], parts[1]);

        List<String> updated = new ArrayList<>(current);
        updated.remove(imageUrl);
        product.setImages(updated);
        if (!updated.contains(product.getThumbnail())) {
            product.setThumbnail(updated.isEmpty() ? null : updated.getFirst());
        }
        Product saved = productRepository.save(product);
        productCacheInvalidationService.evictProductCaches(productId, "PRODUCT_IMAGES_UPDATED");
        return productResponseEnricher.enrichImageResponse(productMapper.toResponse(saved));
    }

    /**
     * Replaces the images list with the caller-supplied ordered list.
     * The first URL becomes the primary/thumbnail image.
     * All supplied URLs must exactly match the product's current image list.
     */
    public ProductResponse reorderImages(String productId, List<String> orderedUrls) {
        Product product = getProduct(productId);

        List<String> current = product.getImages() == null ? List.of() : product.getImages();

        if (!current.containsAll(orderedUrls) || !orderedUrls.containsAll(current)) {
            throw new BadRequestException(
                    "The provided image URLs do not exactly match the product's current image list.");
        }

        product.setImages(new ArrayList<>(orderedUrls));
        product.setThumbnail(orderedUrls.isEmpty() ? null : orderedUrls.getFirst());
        Product saved = productRepository.save(product);
        productCacheInvalidationService.evictProductCaches(productId, "PRODUCT_IMAGES_UPDATED");
        return productResponseEnricher.enrichImageResponse(productMapper.toResponse(saved));
    }

    private Product getProduct(String id) {
        return productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    private String uploadImageToS3(String productId, MultipartFile file) {
        validateConfigured();
        validateFile(file);

        String key = buildKey(productId, file.getOriginalFilename());
        byte[] bytes = readBytes(file);

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(s3Properties.getBucket())
                            .key(key)
                            .contentType(file.getContentType())
                            .contentLength((long) bytes.length)
                            .build(),
                    RequestBody.fromBytes(bytes)
            );
        } catch (S3Exception e) {
            log.error("AWS S3 product image upload failed [bucket={}, key={}]: {}",
                    s3Properties.getBucket(), key, getS3ErrorMessage(e));
            throw new BadRequestException(buildS3ClientMessage("upload", e));
        }

        return buildPublicUrl(key);
    }

    private void deleteImageFromS3(String bucket, String key) {
        validateConfigured();
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            log.error("AWS S3 product image delete failed [bucket={}, key={}]: {}",
                    bucket, key, getS3ErrorMessage(e));
            throw new BadRequestException(buildS3ClientMessage("delete", e));
        }
    }

    private void validateConfigured() {
        if (!StringUtils.hasText(s3Properties.getBucket())) {
            throw new BadRequestException("Chưa cấu hình AWS S3 bucket cho upload ảnh sản phẩm");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Image file must not be empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException("Image size exceeds the 5MB limit");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Unsupported image type. Allowed: JPEG, PNG, WebP, GIF");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new BadRequestException("Failed to read uploaded image");
        }
    }

    private String buildKey(String productId, String originalFilename) {
        String extension = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        return "products/" + productId + "/images/" + UUID.randomUUID() + extension;
    }

    private String buildPublicUrl(String key) {
        String encodedKey = encodeKey(key);
        return "https://" + s3Properties.getBucket() + ".s3." + s3Properties.getRegion()
                + ".amazonaws.com/" + encodedKey;
    }

    private String encodeKey(String key) {
        String[] parts = key.split("/");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20");
        }
        return String.join("/", parts);
    }

    /**
     * Extracts [bucket, key] from either the old gateway file URL
     * (/api/v1/files/{bucket}/{key}) or the AWS S3 public URL.
     */
    private String[] extractBucketAndKey(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            String[] segments = path.split("/");

            if (segments.length >= 6 && "api".equals(segments[1]) && "v1".equals(segments[2])
                    && "files".equals(segments[3])) {
                return new String[]{segments[4], joinPathSegments(segments, 5)};
            }

            String host = uri.getHost();
            if (host != null && host.endsWith(".amazonaws.com")) {
                String bucket = host.split("\\.s3\\.")[0];
                return new String[]{bucket, stripLeadingSlash(path)};
            }

            throw new BadRequestException("Cannot parse storage URL: " + url);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid image URL format: " + url);
        }
    }

    private String joinPathSegments(String[] segments, int startIndex) {
        List<String> parts = new ArrayList<>();
        for (int i = startIndex; i < segments.length; i++) {
            if (StringUtils.hasText(segments[i])) {
                parts.add(segments[i]);
            }
        }
        return String.join("/", parts);
    }

    private String stripLeadingSlash(String path) {
        return path == null || path.isBlank()
                ? ""
                : path.replaceFirst("^/+", "");
    }

    private String getS3ErrorMessage(S3Exception e) {
        if (e.awsErrorDetails() != null && StringUtils.hasText(e.awsErrorDetails().errorMessage())) {
            return e.awsErrorDetails().errorMessage();
        }
        return e.getMessage();
    }

    private String buildS3ClientMessage(String operation, S3Exception e) {
        String errorCode = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null;

        if (e.statusCode() == 403) {
            return "Failed to " + operation + " product image: S3 access denied";
        }
        if ("NoSuchBucket".equals(errorCode)) {
            return "Failed to " + operation + " product image: S3 bucket does not exist";
        }
        if ("AccessDenied".equals(errorCode)) {
            return "Failed to " + operation + " product image: S3 access denied";
        }
        if ("InvalidAccessKeyId".equals(errorCode) || "SignatureDoesNotMatch".equals(errorCode)) {
            return "Failed to " + operation + " product image: invalid AWS credentials";
        }
        if ("AuthorizationHeaderMalformed".equals(errorCode) || "PermanentRedirect".equals(errorCode)) {
            return "Failed to " + operation + " product image: S3 region is incorrect";
        }

        String message = getS3ErrorMessage(e);
        if (StringUtils.hasText(errorCode)) {
            return "Failed to " + operation + " product image: " + errorCode;
        }
        return StringUtils.hasText(message)
                ? "Failed to " + operation + " product image: " + message
                : "Failed to " + operation + " product image";
    }

}
