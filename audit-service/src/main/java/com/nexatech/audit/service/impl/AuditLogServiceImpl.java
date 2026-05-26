package com.nexatech.audit.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.nexatech.audit.dto.event.AuditLogEvent;
import com.nexatech.audit.dto.response.AuditLogResponse;
import com.nexatech.audit.exception.AppException;
import com.nexatech.audit.exception.ErrorCode;
import com.nexatech.audit.model.AuditLog;
import com.nexatech.audit.repository.AuditLogRepository;
import com.nexatech.audit.service.AuditLogService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditLogServiceImpl implements AuditLogService {

    AuditLogRepository auditLogRepository;
    MongoTemplate mongoTemplate;

    @Override
    public AuditLogResponse save(AuditLogEvent event) {
        if (event == null || !StringUtils.hasText(event.getAction())
                || !StringUtils.hasText(event.getEntityType())
                || !StringUtils.hasText(event.getEntityId())) {
            throw new AppException(ErrorCode.INVALID_AUDIT_PAYLOAD);
        }

        Instant occurredAt = event.getTimestamp() != null
                ? Instant.ofEpochMilli(event.getTimestamp())
                : Instant.now();

        AuditLog entity = AuditLog.builder()
                .action(event.getAction())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .oldValue(event.getOldValue())
                .newValue(event.getNewValue())
                .actor(event.getActor())
                .reason(event.getReason())
                .sourceService(event.getSourceService())
                .message(event.getMessage())
                .metadata(event.getMetadata())
                .timestamp(occurredAt)
                .build();

        AuditLog saved = auditLogRepository.save(entity);
        log.debug("Saved audit action={} entity={}/{} actor={}",
                saved.getAction(), saved.getEntityType(), saved.getEntityId(), saved.getActor());

        return toResponse(saved);
    }

    @Override
    public AuditLogResponse getById(String id) {
        return auditLogRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.AUDIT_LOG_NOT_FOUND));
    }

    @Override
    public Page<AuditLogResponse> search(
            String action,
            String entityType,
            String entityId,
            String actor,
            String sourceService,
            Instant from,
            Instant to,
            Pageable pageable) {

        Query query = new Query().with(pageable);

        if (StringUtils.hasText(action)) {
            query.addCriteria(Criteria.where("action").is(action));
        }
        if (StringUtils.hasText(entityType)) {
            query.addCriteria(Criteria.where("entity_type").is(entityType));
        }
        if (StringUtils.hasText(entityId)) {
            query.addCriteria(Criteria.where("entity_id").is(entityId));
        }
        if (StringUtils.hasText(actor)) {
            query.addCriteria(Criteria.where("actor").is(actor));
        }
        if (StringUtils.hasText(sourceService)) {
            query.addCriteria(Criteria.where("source_service").is(sourceService));
        }
        if (from != null || to != null) {
            Criteria range = Criteria.where("timestamp");
            if (from != null) {
                range = range.gte(from);
            }
            if (to != null) {
                range = range.lte(to);
            }
            query.addCriteria(range);
        }

        List<AuditLog> content = mongoTemplate.find(query, AuditLog.class);

        Query countQuery = Query.of(query).limit(-1).skip(-1);
        long total = mongoTemplate.count(countQuery, AuditLog.class);

        List<AuditLogResponse> mapped = content.stream().map(this::toResponse).toList();
        return new PageImpl<>(mapped, pageable, total);
    }

    private AuditLogResponse toResponse(AuditLog entity) {
        return AuditLogResponse.builder()
                .id(entity.getId())
                .action(entity.getAction())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .oldValue(entity.getOldValue())
                .newValue(entity.getNewValue())
                .actor(entity.getActor())
                .reason(entity.getReason())
                .sourceService(entity.getSourceService())
                .message(entity.getMessage())
                .metadata(entity.getMetadata())
                .timestamp(entity.getTimestamp())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
