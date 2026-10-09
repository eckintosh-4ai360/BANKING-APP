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
 * A repayment and how it was split (the parts add up to the amount: database check). Never changes.
 */
@Getter
@Entity
@Immutable
@Table(name = "loan_repayment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanRepayment {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "financial_transaction_id", nullable = false)
    private UUID financialTransactionId;

    @Column(name = "source", nullable = false, length = 10)
    private String source;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "penalty_allocated", nullable = false, precision = 19, scale = 4)
    private BigDecimal penaltyAllocated;

    @Column(name = "fee_allocated", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeAllocated;

    @Column(name = "interest_allocated", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestAllocated;

    @Column(name = "principal_allocated", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAllocated;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Builder
    @SuppressWarnings("java:S107")
    private LoanRepayment(UUID id, UUID tenantId, UUID loanId, UUID financialTransactionId, String source,
                          BigDecimal amount, BigDecimal penaltyAllocated, BigDecimal feeAllocated,
                          BigDecimal interestAllocated, BigDecimal principalAllocated, LocalDate businessDate,
                          UUID receivedBy, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.loanId = loanId;
        this.financialTransactionId = financialTransactionId;
        this.source = source;
        this.amount = amount;
        this.penaltyAllocated = penaltyAllocated;
        this.feeAllocated = feeAllocated;
        this.interestAllocated = interestAllocated;
        this.principalAllocated = principalAllocated;
        this.businessDate = businessDate;
        this.receivedBy = receivedBy;
        this.createdAt = createdAt;
    }
}
