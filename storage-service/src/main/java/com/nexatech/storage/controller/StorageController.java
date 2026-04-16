package com.nexatech.storage.controller;

import com.nexatech.storage.dto.response.ApiResponse;
import com.nexatech.storage.dto.response.FileUploadResponse;
import com.nexatech.storage.service.StorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    /**
     * Internal upload endpoint — NOT exposed via Kong.
     * Called by product-service (and other services) via Feign over the internal Docker network.
     *
     * POST /internal/files/upload
     */
    @PostMapping(value = "/internal/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String bucket) {
        FileUploadResponse response = storageService.uploadFile(file, bucket);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /**
     * Internal delete endpoint — NOT exposed via Kong.
     * Called by product-service via Feign.
     *
     * DELETE /internal/files?bucket=&key=
     */
    @DeleteMapping("/internal/files")
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestParam String bucket,
            @RequestParam String key) {
        storageService.deleteFile(bucket, key);
        return ResponseEntity.ok(ApiResponse.ok(null, "File deleted successfully"));
    }

    /**
     * Public file retrieval — exposed via Kong at /api/v1/files/{bucket}/{key}.
     * Streams the file content directly (no presigned URL redirect, avoids host mismatch).
     *
     * GET /api/v1/files/{bucket}/{key}
     */
    @GetMapping("/api/v1/files/{bucket}/{key}")
    public void getFile(
            @PathVariable String bucket,
            @PathVariable String key,
            HttpServletResponse response) {
        storageService.streamFile(bucket, key, response);
    }
}
