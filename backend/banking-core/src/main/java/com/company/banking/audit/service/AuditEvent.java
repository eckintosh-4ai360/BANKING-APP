package com.company.banking.audit.service;

import com.company.banking.common.security.ActorType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An auditable action. {@code before}/{@code after} must be purpose-built snapshot records, never JPA entities,
 * so secrets (hashes, tokens, PINs) can't reach the audit trail.
 *
 * @param actor explicit actor for unauthenticated flows (e.g. login); otherwise the current actor is used
 */
public record AuditEvent(
        String action,
        String resourceType,
        String resourceId,
        String resourceReference,
        UUID branchId,
        Object before,
        Object after,
        Map<String, Object> metadata,
        AuditOutcome outcome,
        ActorRef actor) {

    public static Builder builder(String action, String resourceType) {
        return new Builder(action, resourceType);
    }

    public record ActorRef(ActorType type, UUID id, String name) {
    }

    public static final class Builder {
        private final String action;
        private final String resourceType;
        private String resourceId;
        private String resourceReference;
        private UUID branchId;
        private Object before;
        private Object after;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private AuditOutcome outcome = AuditOutcome.SUCCESS;
        private ActorRef actor;

        private Builder(String action, String resourceType) {
            this.action = action;
            this.resourceType = resourceType;
        }

        public Builder resourceId(Object resourceId) {
            this.resourceId = resourceId == null ? null : resourceId.toString();
            return this;
        }

        public Builder resourceReference(String resourceReference) {
            this.resourceReference = resourceReference;
            return this;
        }

        public Builder branchId(UUID branchId) {
            this.branchId = branchId;
            return this;
        }

        public Builder before(Object before) {
            this.before = before;
            return this;
        }

        public Builder after(Object after) {
            this.after = after;
            return this;
        }

        public Builder metadata(String key, Object value) {
            if (value != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        public Builder outcome(AuditOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder actor(ActorType type, UUID id, String name) {
            this.actor = new ActorRef(type, id, name);
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(action, resourceType, resourceId, resourceReference, branchId, before, after,
                    Map.copyOf(metadata), outcome, actor);
        }
    }
}
