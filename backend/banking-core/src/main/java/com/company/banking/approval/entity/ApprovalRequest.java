package com.company.banking.approval.entity;

import com.company.banking.approval.model.ApprovalStatus;
import com.company.banking.approval.model.ApprovalType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.DynamicUpdate;

/**
 * An action waiting for a second person. What was asked for (type, amount, payload) never changes; only the
 * decision is recorded, once (enforced by the database).
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "approval_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRequest {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, updatable = false, length = 30)
    private ApprovalType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ApprovalStatus status;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "amount", updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", updatable = false, length = 3)
    private String currency;

    @Column(name = "resource_type", updatable = false, length = 40)
    private String resourceType;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "summary", nullable = false, updatable = false, length = 300)
    private String summary;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 300)
    private String decisionNote;

    @Column(name = "result_resource_id")
    private UUID resultResourceId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @SuppressWarnings("java:S107")
    public ApprovalRequest(UUID id, UUID tenantId, ApprovalType requestType, UUID branchId, BigDecimal amount,
                           String currency, String resourceType, UUID resourceId, String summary, String payload,
                           UUID requestedBy, Instant requestedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.requestType = requestType;
        this.status = ApprovalStatus.PENDING;
        this.branchId = branchId;
        this.amount = amount;
        this.currency = currency;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.summary = summary;
        this.payload = payload;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
    }

    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }

    public void decide(ApprovalStatus outcome, UUID by, Instant at, String note, UUID resultResourceId) {
        if (!isPending() || outcome == ApprovalStatus.PENDING) {
            throw new IllegalStateException("Only a pending request can be decided");
        }
        this.status = outcome;
        this.decidedBy = by;
        this.decidedAt = at;
        this.decisionNote = note;
        this.resultResourceId = resultResourceId;
    }
}
