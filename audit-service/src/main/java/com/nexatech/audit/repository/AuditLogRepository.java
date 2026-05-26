package com.nexatech.audit.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.nexatech.audit.model.AuditLog;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
}
