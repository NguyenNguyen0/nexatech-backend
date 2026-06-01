package com.nexatech.product.controller;

import com.nexatech.product.constant.PermissionsAccept;
import com.nexatech.product.dto.request.ImageDeleteRequest;
import com.nexatech.product.dto.request.ImageReorderRequest;
import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.ProductResponse;
import com.nexatech.product.security.RequiresPermission;
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(PermissionsAccept.Product.IMAGE_WRITE)
    public ApiResponse<ProductResponse> uploadImages(
            @PathVariable String id,
            @RequestPart("files") List<MultipartFile> files) {
        return ApiResponse.ok(productImageService.uploadImages(id, files), "Images uploaded successfully");
    }

    @DeleteMapping
    @RequiresPermission(PermissionsAccept.Product.IMAGE_WRITE)
    public ApiResponse<ProductResponse> deleteImage(
            @PathVariable String id,
            @Valid @RequestBody ImageDeleteRequest request) {
        return ApiResponse.ok(productImageService.deleteImage(id, request.getImageUrl()), "Image deleted successfully");
    }

    @PutMapping("/reorder")
    @RequiresPermission(PermissionsAccept.Product.IMAGE_WRITE)
    public ApiResponse<ProductResponse> reorderImages(
            @PathVariable String id,
            @Valid @RequestBody ImageReorderRequest request) {
        return ApiResponse.ok(productImageService.reorderImages(id, request.getImageUrls()), "Images reordered successfully");
    }
}
