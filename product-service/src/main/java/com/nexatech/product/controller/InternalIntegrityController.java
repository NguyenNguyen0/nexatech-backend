package com.nexatech.product.controller;

import com.nexatech.product.dto.response.ApiResponse;
import com.nexatech.product.dto.response.IdReferenceIntegrityResponse;
import com.nexatech.product.service.DataIntegrityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/integrity")
@RequiredArgsConstructor
public class InternalIntegrityController {

    private final DataIntegrityService dataIntegrityService;

    @GetMapping("/id-references")
    public ApiResponse<IdReferenceIntegrityResponse> getIdReferenceIntegrity() {
        return ApiResponse.ok(dataIntegrityService.checkIdReferences());
    }
}
