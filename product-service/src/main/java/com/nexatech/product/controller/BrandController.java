package com.nexatech.product.controller;

import com.nexatech.product.constant.PermissionsAccept;
import com.nexatech.product.dto.request.BrandRequest;
import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.BrandResponse;
import com.nexatech.product.security.RequiresPermission;
import com.nexatech.product.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    // @RequiresPermission(PermissionsAccept.Brand.WRITE)
    public ApiResponse<BrandResponse> createBrand(@Valid @RequestBody BrandRequest request) {
        return ApiResponse.ok(brandService.createBrand(request));
    }

    @PutMapping("/{id}")
    // @RequiresPermission(PermissionsAccept.Brand.WRITE)
    public ApiResponse<BrandResponse> updateBrand(
            @PathVariable String id,
            @Valid @RequestBody BrandRequest request) {
        return ApiResponse.ok(brandService.updateBrand(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<BrandResponse> getBrand(@PathVariable String id) {
        return ApiResponse.ok(brandService.getBrand(id));
    }

    @GetMapping("/slug/{slug}")
    public ApiResponse<BrandResponse> getBrandBySlug(@PathVariable String slug) {
        return ApiResponse.ok(brandService.getBrandBySlug(slug));
    }

    @GetMapping
    public ApiResponse<List<BrandResponse>> getAllBrands(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeleted) {
        return ApiResponse.ok(brandService.getAllBrands(active, includeDeleted));
    }

    @DeleteMapping("/{id}")
    // @RequiresPermission(PermissionsAccept.Brand.WRITE)
    public ApiResponse<Void> deleteBrand(@PathVariable String id) {
        brandService.deleteBrand(id);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/restore")
    // @RequiresPermission(PermissionsAccept.Brand.WRITE)
    public ApiResponse<BrandResponse> restoreBrand(@PathVariable String id) {
        return ApiResponse.ok(brandService.restoreBrand(id));
    }
}
