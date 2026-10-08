package com.company.banking.transaction.entity;

import com.company.banking.transaction.model.TransactionChannel;
import com.company.banking.transaction.model.TransactionStatus;
import com.company.banking.transaction.model.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

/**
 * The business record of one money movement. The money itself is the journal it points to; this row says what
 * happened, between which accounts, through which channel and who did it. It never changes except to record its
 * reversal (enforced by the database).
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "financial_transaction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialTransaction implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "reference", nullable = false, updatable = false, length = 40)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, updatable = false, length = 20)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false, length = 10)
    private TransactionChannel channel;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "fee_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "debit_account_id", updatable = false)
    private UUID debitAccountId;

    @Column(name = "credit_account_id", updatable = false)
    private UUID creditAccountId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "journal_entry_id", nullable = false, updatable = false)
    private UUID journalEntryId;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "value_date", nullable = false, updatable = false)
    private LocalDate valueDate;

    @Column(name = "narration", updatable = false, length = 200)
    private String narration;

    @Column(name = "external_reference", updatable = false, length = 60)
    private String externalReference;

    @Column(name = "idempotency_key", updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "initiated_by", updatable = false)
    private UUID initiatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_by", updatable = false)
    private UUID approvedBy;

    @Column(name = "approval_request_id", updatable = false)
    private UUID approvalRequestId;

    @Column(name = "reversal_journal_entry_id")
    private UUID reversalJournalEntryId;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by")
    private UUID reversedBy;

    @Column(name = "reversal_reason", length = 300)
    private String reversalReason;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newEntity = true;

    @SuppressWarnings("java:S107")
    public FinancialTransaction(UUID id, UUID tenantId, String reference, TransactionType transactionType,
                                TransactionChannel channel, String currency, BigDecimal amount, BigDecimal feeAmount,
                                UUID debitAccountId, UUID creditAccountId, UUID branchId, UUID journalEntryId,
                                LocalDate businessDate, LocalDate valueDate, String narration,
                                String externalReference, String idempotencyKey, UUID initiatedBy, UUID approvedBy,
                                UUID approvalRequestId, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.reference = reference;
        this.transactionType = transactionType;
        this.status = TransactionStatus.POSTED;
        this.channel = channel;
        this.currency = currency;
        this.amount = amount;
        this.feeAmount = feeAmount;
        this.debitAccountId = debitAccountId;
        this.creditAccountId = creditAccountId;
        this.branchId = branchId;
        this.journalEntryId = journalEntryId;
        this.businessDate = businessDate;
        this.valueDate = valueDate;
        this.narration = narration;
        this.externalReference = externalReference;
        this.idempotencyKey = idempotencyKey;
        this.initiatedBy = initiatedBy;
        this.approvedBy = approvedBy;
        this.approvalRequestId = approvalRequestId;
        this.createdAt = createdAt;
    }

    /**
     * Records the reversal (the mirror journal is already posted). A transaction is reversed at most once.
     */
    public void markReversed(UUID reversalJournalId, Instant at, UUID by, String reason) {
        if (status != TransactionStatus.POSTED) {
            throw new IllegalStateException("Only a posted transaction can be reversed");
        }
        this.status = TransactionStatus.REVERSED;
        this.reversalJournalEntryId = reversalJournalId;
        this.reversedAt = at;
        this.reversedBy = by;
        this.reversalReason = reason;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newEntity = false;
    }
}
