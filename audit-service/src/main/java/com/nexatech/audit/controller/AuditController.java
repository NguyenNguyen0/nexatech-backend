package com.nexatech.audit.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexatech.audit.constant.PermissionsAccept;
import com.nexatech.audit.dto.base.ApiResponse;
import com.nexatech.audit.dto.base.PageResponse;
import com.nexatech.audit.dto.response.AuditLogResponse;
import com.nexatech.audit.security.RequiresPermission;
import com.nexatech.audit.service.AuditFacets;
import com.nexatech.audit.service.AuditLogService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/audit")
public class AuditController {

    AuditLogService auditLogService;

    @GetMapping
    @RequiresPermission(PermissionsAccept.Audit.READ)
    public ApiResponse<PageResponse<AuditLogResponse>> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String sourceService,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort sort = Sort.Direction.ASC.name().equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, sort);

        Page<AuditLogResponse> result = auditLogService.search(
                action, entityType, entityId, actor, sourceService, from, to, pageable);

        return ApiResponse.<PageResponse<AuditLogResponse>>builder()
                .result(new PageResponse<>(result))
                .build();
    }

    @GetMapping("/{id}")
    @RequiresPermission(PermissionsAccept.Audit.READ)
    public ApiResponse<AuditLogResponse> getById(@PathVariable String id) {
        return ApiResponse.<AuditLogResponse>builder()
                .result(auditLogService.getById(id))
                .build();
    }

    @GetMapping("/trace/{lookupId}")
    @RequiresPermission(PermissionsAccept.Audit.READ)
    public ApiResponse<List<AuditLogResponse>> trace(@PathVariable String lookupId) {
        return ApiResponse.<List<AuditLogResponse>>builder()
                .result(auditLogService.trace(lookupId))
                .build();
    }

    /**
     * Distinct facets currently present in the collection.
     * The admin UI uses this to build filter options dynamically —
     * new services / actions show up in the filters automatically as soon as
     * they emit their first audit event.
     */
    @GetMapping("/facets")
    @RequiresPermission(PermissionsAccept.Audit.READ)
    public ApiResponse<AuditFacets> facets() {
        return ApiResponse.<AuditFacets>builder()
                .result(auditLogService.facets())
                .build();
    }
}
