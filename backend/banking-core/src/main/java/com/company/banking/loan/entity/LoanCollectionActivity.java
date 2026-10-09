package com.company.banking.loan.entity;

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
 * A collection call, visit, message or promise to pay on a loan. Only a promise's status ever changes (end-of-day
 * marks it kept or broken on its date).
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "loan_collection_activity")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanCollectionActivity {

    public enum Type { CALL, VISIT, SMS, LETTER, PROMISE, OTHER }

    public enum PromiseStatus { OPEN, KEPT, BROKEN }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "loan_id", nullable = false, updatable = false)
    private UUID loanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, updatable = false, length = 10)
    private Type activityType;

    @Column(name = "note", nullable = false, updatable = false, length = 1000)
    private String note;

    @Column(name = "days_past_due", nullable = false, updatable = false)
    private int daysPastDue;

    @Column(name = "promised_amount", updatable = false, precision = 19, scale = 4)
    private BigDecimal promisedAmount;

    @Column(name = "promised_date", updatable = false)
    private LocalDate promisedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "promise_status", length = 10)
    private PromiseStatus promiseStatus;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @SuppressWarnings("java:S107")
    public LoanCollectionActivity(UUID id, UUID tenantId, UUID loanId, Type activityType, String note, int daysPastDue,
                                  BigDecimal promisedAmount, LocalDate promisedDate, LocalDate businessDate,
                                  UUID createdBy, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.loanId = loanId;
        this.activityType = activityType;
        this.note = note;
        this.daysPastDue = daysPastDue;
        this.promisedAmount = promisedAmount;
        this.promisedDate = promisedDate;
        this.promiseStatus = promisedAmount == null ? null : PromiseStatus.OPEN;
        this.businessDate = businessDate;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void settlePromise(boolean kept) {
        this.promiseStatus = kept ? PromiseStatus.KEPT : PromiseStatus.BROKEN;
    }
}
