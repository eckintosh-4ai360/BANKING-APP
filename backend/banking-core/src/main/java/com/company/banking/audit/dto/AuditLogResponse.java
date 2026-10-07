package com.company.banking.audit.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
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
        JsonNode before,
        JsonNode after,
        JsonNode metadata,
        String ipAddress,
        String userAgent,
        String deviceId,
        String correlationId) {
}
