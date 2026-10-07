package com.company.banking.kyc.entity;

import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One verification of a customer to a tier: capture → submit → review → approve / reject / return. The person who
 * submits can never decide (also enforced by a database check).
 */
@Getter
@Entity
@Table(name = "kyc_case")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KycCase extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false, updatable = false, length = 20)
    private KycCaseType caseType;

    @Column(name = "target_tier_code", nullable = false, updatable = false, length = 30)
    private String targetTierCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KycCaseStatus status;

    @Column(name = "opened_by", updatable = false)
    private UUID openedBy;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "assigned_risk_level", length = 20)
    private String assignedRiskLevel;

    public KycCase(UUID id, UUID tenantId, UUID customerId, UUID branchId, KycCaseType caseType,
                   String targetTierCode, UUID openedBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.branchId = branchId;
        this.caseType = caseType;
        this.targetTierCode = targetTierCode;
        this.openedBy = openedBy;
        this.status = KycCaseStatus.OPEN;
    }

    public void submit(UUID actor, Instant now) {
        moveTo(KycCaseStatus.PENDING_REVIEW);
        this.submittedBy = actor;
        this.submittedAt = now;
        this.decisionNote = null;
    }

    public void returnForCorrection(UUID reviewer, String note) {
        assertNotSubmitter(reviewer);
        moveTo(KycCaseStatus.RETURNED);
        this.decisionNote = note;
    }

    public void approve(UUID checker, String riskLevel, String note, Instant now) {
        assertNotSubmitter(checker);
        moveTo(KycCaseStatus.APPROVED);
        this.decidedBy = checker;
        this.decidedAt = now;
        this.assignedRiskLevel = riskLevel;
        this.decisionNote = note;
    }

    public void reject(UUID checker, String note, Instant now) {
        assertNotSubmitter(checker);
        moveTo(KycCaseStatus.REJECTED);
        this.decidedBy = checker;
        this.decidedAt = now;
        this.decisionNote = note;
    }

    public void cancel(String note) {
        moveTo(KycCaseStatus.CANCELLED);
        this.decisionNote = note;
    }

    private void assertNotSubmitter(UUID actor) {
        if (actor == null || Objects.equals(actor, submittedBy)) {
            throw new BankingException(CommonErrorCode.FOUR_EYES_VIOLATION);
        }
    }

    private void moveTo(KycCaseStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BankingException(CommonErrorCode.INVALID_STATE_TRANSITION,
                    "A KYC case cannot move from " + status + " to " + target + ".");
        }
        this.status = target;
    }
}
