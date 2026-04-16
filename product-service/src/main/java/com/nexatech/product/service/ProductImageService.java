package com.nexatech.product.service;

import com.nexatech.product.client.StorageClient;
import com.nexatech.product.dto.response.ProductResponse;
import com.nexatech.product.dto.response.StorageUploadResult;
import com.nexatech.product.exception.BadRequestException;
import com.nexatech.product.exception.ResourceNotFoundException;
import com.nexatech.product.mapper.ProductMapper;
import com.nexatech.product.model.Brand;
import com.nexatech.product.model.Product;
import com.nexatech.product.repository.BrandRepository;
import com.nexatech.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private static final int MAX_IMAGES = 10;

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final StorageClient storageClient;
    private final ProductMapper productMapper;

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
            StorageUploadResult result = storageClient.upload(file, null);
            if (result == null || result.getData() == null || result.getData().getUrl() == null) {
                throw new BadRequestException("Upload failed for file: " + file.getOriginalFilename());
            }
            current.add(result.getData().getUrl());
            log.info("Uploaded image for product [{}]: {}", productId, result.getData().getUrl());
        }

        product.setImages(current);
        return enrichWithBrandName(productMapper.toResponse(productRepository.save(product)));
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
        storageClient.delete(parts[0], parts[1]);

        List<String> updated = new ArrayList<>(current);
        updated.remove(imageUrl);
        product.setImages(updated);
        return enrichWithBrandName(productMapper.toResponse(productRepository.save(product)));
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
        return enrichWithBrandName(productMapper.toResponse(productRepository.save(product)));
    }

    // -------------------------------------------------------------------------

    private Product getProduct(String id) {
        return productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    /**
     * Extracts [bucket, key] from a URL of the form:
     *   http://host:port/api/v1/files/{bucket}/{key}
     */
    private String[] extractBucketAndKey(String url) {
        try {
            String path = URI.create(url).getPath(); // /api/v1/files/{bucket}/{key}
            String[] segments = path.split("/");
            // segments: ["", "api", "v1", "files", "{bucket}", "{key}"]
            if (segments.length < 6) {
                throw new BadRequestException("Cannot parse storage URL: " + url);
            }
            return new String[]{segments[4], segments[5]};
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid image URL format: " + url);
        }
    }

    private ProductResponse enrichWithBrandName(ProductResponse response) {
        if (StringUtils.hasText(response.getBrandId())) {
            response.setBrandName(
                    brandRepository.findById(response.getBrandId())
                            .map(Brand::getName)
                            .orElse(null));
        }
        return response;
    }
}
