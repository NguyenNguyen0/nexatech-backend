package com.nexatech.storage.controller;

import com.nexatech.storage.dto.response.ApiResponse;
import com.nexatech.storage.dto.response.FileUploadResponse;
import com.nexatech.storage.service.StorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    /**
     * Internal upload endpoint — NOT exposed via Kong.
     * Called by product-service (and other services) via Feign over the internal Docker network.
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
     */
    @GetMapping("/api/v1/files/{bucket}/{key}")
    public void getFile(
            @PathVariable String bucket,
            @PathVariable String key,
            HttpServletResponse response) {
        storageService.streamFile(bucket, key, response);
    }
}
