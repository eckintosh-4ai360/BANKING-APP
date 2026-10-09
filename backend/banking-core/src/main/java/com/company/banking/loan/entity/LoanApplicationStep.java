package com.company.banking.loan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * One step of an application's workflow, by whom and when. Never changes.
 */
@Getter
@Entity
@Immutable
@Table(name = "loan_application_step")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanApplicationStep {

    public enum Type { SUBMIT, ASSESS, RECOMMEND, APPROVE, SECOND_APPROVE, REJECT, WITHDRAW, DISBURSE }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 15)
    private Type stepType;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "note", length = 1000)
    private String note;

    public LoanApplicationStep(UUID id, UUID tenantId, UUID applicationId, Type stepType, UUID actorId,
                               Instant occurredAt, String note) {
        this.id = id;
        this.tenantId = tenantId;
        this.applicationId = applicationId;
        this.stepType = stepType;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
        this.note = note;
    }
}
