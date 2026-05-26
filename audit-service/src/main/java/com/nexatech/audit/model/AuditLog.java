package com.nexatech.audit.model;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Immutable audit record — INSERT only, never UPDATE/DELETE.
 * Producer services publish events to the Kafka topic {@code audit-topic};
 * the consumer in this service persists them into the {@code audit_logs} collection.
 */
@Document(collection = "audit_logs")
@CompoundIndexes({
        @CompoundIndex(name = "idx_entity", def = "{ 'entity_type': 1, 'entity_id': 1 }"),
        @CompoundIndex(name = "idx_action_timestamp", def = "{ 'action': 1, 'timestamp': -1 }")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuditLog {

    @Id
    String id;

    /** Action name, e.g. ORDER_CREATED, PAYMENT_REFUNDED, USER_DISABLED. */
    @Field("action")
    @Indexed
    String action;

    /** Entity type, e.g. Order, Payment, User, Product. */
    @Field("entity_type")
    String entityType;

    /** ID of the affected entity (kept as String to support UUID, ObjectId, numeric ids…). */
    @Field("entity_id")
    String entityId;

    /** Snapshot before the change (JSON string or plain text). Null on create. */
    @Field("old_value")
    String oldValue;

    /** Snapshot after the change. */
    @Field("new_value")
    String newValue;

    /** Performer (userId / email / "system"). */
    @Field("actor")
    @Indexed
    String actor;

    /** Short description of the operation context. */
    @Field("reason")
    String reason;

    /** Origin service of the event, e.g. order-service, payment-service. */
    @Field("source_service")
    @Indexed
    String sourceService;

    /** Human-readable message attached to the event. */
    @Field("message")
    String message;

    /** Free-form metadata (correlationId, traceId, IP, userAgent…). */
    @Field("metadata")
    Map<String, Object> metadata;

    /** When the event occurred on the producer side. */
    @Field("timestamp")
    @Indexed
    Instant timestamp;

    /** When the record was persisted by audit-service. */
    @Field("created_at")
    @CreatedDate
    Instant createdAt;
}
