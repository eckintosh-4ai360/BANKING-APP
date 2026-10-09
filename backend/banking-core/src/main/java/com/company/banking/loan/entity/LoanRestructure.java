package com.company.banking.loan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * A restructure of a loan: the schedule version it replaced, what was carried into the new one, and who asked and
 * approved. Never changes.
 */
@Getter
@Entity
@Immutable
@Table(name = "loan_restructure")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanRestructure {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "from_version", nullable = false)
    private int fromVersion;

    @Column(name = "to_version", nullable = false)
    private int toVersion;

    @Column(name = "principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal principal;

    @Column(name = "interest_carried", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestCarried;

    @Column(name = "penalty_carried", nullable = false, precision = 19, scale = 4)
    private BigDecimal penaltyCarried;

    @Column(name = "installments", nullable = false)
    private int installments;

    @Column(name = "first_due_date", nullable = false)
    private LocalDate firstDueDate;

    @Column(name = "band_at_restructure", length = 20)
    private String bandAtRestructure;

    @Column(name = "hold_band_days", nullable = false)
    private int holdBandDays;

    @Column(name = "reason", nullable = false, length = 300)
    private String reason;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "approved_by", nullable = false)
    private UUID approvedBy;

    @Column(name = "approval_request_id", nullable = false)
    private UUID approvalRequestId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Builder
    @SuppressWarnings("java:S107")
    private LoanRestructure(UUID id, UUID tenantId, UUID loanId, int fromVersion, int toVersion, BigDecimal principal,
                            BigDecimal interestCarried, BigDecimal penaltyCarried, int installments,
                            LocalDate firstDueDate, String bandAtRestructure, int holdBandDays, String reason,
                            UUID requestedBy, UUID approvedBy, UUID approvalRequestId, LocalDate businessDate,
                            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.loanId = loanId;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.principal = principal;
        this.interestCarried = interestCarried;
        this.penaltyCarried = penaltyCarried;
        this.installments = installments;
        this.firstDueDate = firstDueDate;
        this.bandAtRestructure = bandAtRestructure;
        this.holdBandDays = holdBandDays;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.approvedBy = approvedBy;
        this.approvalRequestId = approvalRequestId;
        this.businessDate = businessDate;
        this.createdAt = createdAt;
    }
}
