package com.nexatech.audit.service;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Distinct values currently present in the {@code audit_logs} collection.
 * Used by the admin UI to build filter options dynamically — when a new
 * service / action shows up, it appears in the filters automatically.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuditFacets {

    List<String> actions;
    List<String> entityTypes;
    List<String> sourceServices;
}
