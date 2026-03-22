package com.nexatech.order.controller;

import com.nexatech.order.dto.CreateReturnRequest;
import com.nexatech.order.dto.ReturnResponse;
import com.nexatech.order.dto.response.ApiResponse;
import com.nexatech.order.dto.response.PageResponse;
import com.nexatech.order.service.OrderReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * API quản lý yêu cầu trả hàng.
 *
 * Customer:
 *   POST   /api/v1/returns?orderId=...  — tạo yêu cầu
 *   GET    /api/v1/returns/{id}         — xem yêu cầu
 *   GET    /api/v1/returns/my           — lịch sử của mình
 *
 * Admin:
 *   PATCH  /api/v1/returns/{id}/approve — duyệt
 *   PATCH  /api/v1/returns/{id}/reject  — từ chối
 */
@RestController
@RequestMapping("/api/v1/returns")
@RequiredArgsConstructor
public class OrderReturnController {

    private final OrderReturnService returnService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReturnResponse> createReturn(
            @RequestHeader("X-User-Id") UUID customerId,
            @RequestParam UUID orderId,
            @Valid @RequestBody CreateReturnRequest request) {
        return ApiResponse.ok(returnService.createReturn(customerId, orderId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReturnResponse> getReturn(@PathVariable UUID id) {
        return ApiResponse.ok(returnService.getReturn(id));
    }

    @GetMapping("/my")
    public ApiResponse<PageResponse<ReturnResponse>> getMyReturns(
            @RequestHeader("X-User-Id") UUID customerId,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(returnService.getMyReturns(customerId, pageable)));
    }

    // ==================== Admin endpoints ====================

    @PatchMapping("/{id}/approve")
    public ApiResponse<ReturnResponse> approveReturn(@PathVariable UUID id) {
        return ApiResponse.ok(returnService.approveReturn(id));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<ReturnResponse> rejectReturn(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "") String note) {
        return ApiResponse.ok(returnService.rejectReturn(id, note));
    }
}
