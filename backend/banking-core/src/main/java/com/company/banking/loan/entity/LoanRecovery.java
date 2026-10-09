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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Money recovered on a written-off loan (recognised as recovery income). Never changes.
 */
@Getter
@Entity
@Immutable
@Table(name = "loan_recovery")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanRecovery {

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

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @SuppressWarnings("java:S107")
    public LoanRecovery(UUID id, UUID tenantId, UUID loanId, UUID financialTransactionId, String source,
                        BigDecimal amount, LocalDate businessDate, UUID receivedBy, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.loanId = loanId;
        this.financialTransactionId = financialTransactionId;
        this.source = source;
        this.amount = amount;
        this.businessDate = businessDate;
        this.receivedBy = receivedBy;
        this.createdAt = createdAt;
    }
}
