package com.company.banking.fieldops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A customer looked after by a field officer, from {@code assignedAt} until {@code endedAt}. At most one assignment
 * per customer is open at a time (database index).
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "customer_assignment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerAssignment {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "officer_id", nullable = false, updatable = false)
    private UUID officerId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by", updatable = false)
    private UUID assignedBy;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ended_by")
    private UUID endedBy;

    @Column(name = "end_reason", length = 300)
    private String endReason;

    public CustomerAssignment(UUID id, UUID tenantId, UUID customerId, UUID officerId, Instant assignedAt,
                              UUID assignedBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.officerId = officerId;
        this.assignedAt = assignedAt;
        this.assignedBy = assignedBy;
    }

    public void end(Instant at, UUID by, String reason) {
        this.endedAt = at;
        this.endedBy = by;
        this.endReason = reason;
    }

    public boolean isActive() {
        return endedAt == null;
    }
}
