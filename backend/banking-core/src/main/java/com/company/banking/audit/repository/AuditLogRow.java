package com.company.banking.audit.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * One persisted audit record. JSON columns are carried as serialized text.
 */
public record AuditLogRow(
        UUID id,
        UUID tenantId,
        Instant occurredAt,
        String actorType,
        UUID actorId,
        String actorName,
        String action,
        String outcome,
        String resourceType,
        String resourceId,
        String resourceReference,
        UUID branchId,
        String beforeState,
        String afterState,
        String metadata,
        String ipAddress,
        String userAgent,
        String deviceId,
        String correlationId) {
}
