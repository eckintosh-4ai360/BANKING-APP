package com.company.banking.audit.service;

import com.company.banking.audit.repository.AuditLogRepository;
import com.company.banking.audit.repository.AuditLogRow;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.ActorType;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.common.web.RequestMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the audit trail. The tenant comes from the tenant context and the actor from the security context
 * (or the event's explicit actor), so callers can't misattribute records.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * Records within the caller's transaction: the audit row exists if and only if the business change commits.
     */
    @Transactional
    public void record(AuditEvent event) {
        repository.insert(toRow(event));
    }

    /**
     * Records in a separate transaction that commits even if the caller rolls back. Use for security events
     * (failed logins, denied access) that must survive the failure they describe.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(AuditEvent event) {
        repository.insert(toRow(event));
    }

    private AuditLogRow toRow(AuditEvent event) {
        ResolvedActor actor = resolveActor(event);
        RequestMetadata request = RequestMetadata.current();
        return new AuditLogRow(
                UuidV7.next(),
                TenantContext.currentTenantId().orElse(null),
                Instant.now(clock),
                actor.type().name(),
                actor.id(),
                actor.name(),
                event.action(),
                event.outcome().name(),
                event.resourceType(),
                event.resourceId(),
                event.resourceReference(),
                event.branchId(),
                toJson(event.before()),
                toJson(event.after()),
                event.metadata() == null || event.metadata().isEmpty() ? null : toJson(event.metadata()),
                request.ipAddress(),
                request.userAgent(),
                request.deviceId(),
                request.correlationId());
    }

    private static ResolvedActor resolveActor(AuditEvent event) {
        if (event.actor() != null) {
            return new ResolvedActor(event.actor().type(), event.actor().id(), event.actor().name());
        }
        return CurrentActor.current()
                .map(AuditService::fromActor)
                .orElse(new ResolvedActor(ActorType.ANONYMOUS, null, null));
    }

    private static ResolvedActor fromActor(AuthenticatedActor actor) {
        return new ResolvedActor(actor.type(), actor.id(), actor.username());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        return jsonMapper.writeValueAsString(value);
    }

    private record ResolvedActor(ActorType type, UUID id, String name) {
    }
}
