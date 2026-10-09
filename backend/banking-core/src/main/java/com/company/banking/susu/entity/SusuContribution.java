package com.company.banking.susu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * One expected contribution of a plan. It only moves forward (database trigger): EXPECTED becomes PAID, MISSED
 * (past its due date unpaid) or WAIVED; a MISSED one can still be paid late or waived.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "susu_contribution")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SusuContribution {

    public enum Status { EXPECTED, PAID, MISSED, WAIVED }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Column(name = "cycle_no", nullable = false, updatable = false)
    private int cycleNo;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "collection_id")
    private UUID collectionId;

    @Column(name = "financial_transaction_id")
    private UUID financialTransactionId;

    @Column(name = "waived_by")
    private UUID waivedBy;

    @Column(name = "waive_reason", length = 300)
    private String waiveReason;

    @SuppressWarnings("java:S107")
    public SusuContribution(UUID id, UUID tenantId, UUID planId, int sequenceNo, int cycleNo, LocalDate dueDate,
                            BigDecimal amount) {
        this.id = id;
        this.tenantId = tenantId;
        this.planId = planId;
        this.sequenceNo = sequenceNo;
        this.cycleNo = cycleNo;
        this.dueDate = dueDate;
        this.amount = amount;
        this.status = Status.EXPECTED;
    }

    public void pay(Instant at, UUID collectionId, UUID transactionId) {
        this.status = Status.PAID;
        this.paidAt = at;
        this.collectionId = collectionId;
        this.financialTransactionId = transactionId;
    }

    public void miss() {
        this.status = Status.MISSED;
    }

    public void waive(UUID by, String reason) {
        this.status = Status.WAIVED;
        this.waivedBy = by;
        this.waiveReason = reason;
    }

    public boolean isUnpaid() {
        return status == Status.EXPECTED || status == Status.MISSED;
    }
}
