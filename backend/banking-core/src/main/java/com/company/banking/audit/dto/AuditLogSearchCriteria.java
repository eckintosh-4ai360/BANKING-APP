package com.company.banking.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogSearchCriteria(
        Instant from,
        Instant to,
        UUID actorId,
        String action,
        String resourceType,
        String resourceId,
        String outcome) {
}
