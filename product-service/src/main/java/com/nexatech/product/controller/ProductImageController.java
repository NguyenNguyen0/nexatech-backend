package com.nexatech.product.controller;

import com.nexatech.product.dto.request.ImageDeleteRequest;
import com.nexatech.product.dto.request.ImageReorderRequest;
import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.ProductResponse;
import com.nexatech.product.service.ProductImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products/{id}/images")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService productImageService;

    /**
     * Upload one or more images for a product.
     * Accepts multipart/form-data with field name "files".
     * Validates: at most 10 images per product total.
     * File type/size validation is delegated to storage-service.
     *
     * POST /api/v1/products/{id}/images
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> uploadImages(
            @PathVariable String id,
            @RequestPart("files") List<MultipartFile> files) {
        return ApiResponse.ok(productImageService.uploadImages(id, files), "Images uploaded successfully");
    }

    /**
     * Remove a single image from a product and delete it from storage.
     *
     * DELETE /api/v1/products/{id}/images
     * Body: { "imageUrl": "..." }
     */
    @DeleteMapping
    public ApiResponse<ProductResponse> deleteImage(
            @PathVariable String id,
            @Valid @RequestBody ImageDeleteRequest request) {
        return ApiResponse.ok(productImageService.deleteImage(id, request.getImageUrl()), "Image deleted successfully");
    }

    /**
     * Reorder images. The first URL in the list becomes the primary/thumbnail image.
     * The provided list must contain exactly the same URLs as the current image list.
     *
     * PUT /api/v1/products/{id}/images/reorder
     * Body: { "imageUrls": ["url1", "url2", ...] }
     */
    @PutMapping("/reorder")
    public ApiResponse<ProductResponse> reorderImages(
            @PathVariable String id,
            @Valid @RequestBody ImageReorderRequest request) {
        return ApiResponse.ok(productImageService.reorderImages(id, request.getImageUrls()), "Images reordered successfully");
    }
}
