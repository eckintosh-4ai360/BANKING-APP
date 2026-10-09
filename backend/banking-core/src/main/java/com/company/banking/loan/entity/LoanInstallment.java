package com.company.banking.loan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

/**
 * One installment of a loan's schedule (a version of it) and what repayments settled of it.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "loan_installment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanInstallment implements Persistable<LoanInstallment.Key> {

    @Embeddable
    public record Key(@Column(name = "loan_id") UUID loanId, @Column(name = "schedule_version") int scheduleVersion,
                      @Column(name = "number") int number) implements Serializable {
    }

    @EmbeddedId
    private Key id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "from_date", nullable = false, updatable = false)
    private LocalDate fromDate;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal interestDue;

    @Column(name = "penalty_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal penaltyDue;

    @Column(name = "principal_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalPaid;

    @Column(name = "interest_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestPaid;

    @Column(name = "penalty_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal penaltyPaid;

    @Column(name = "interest_waived", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestWaived;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "penalty_exact", nullable = false, precision = 28, scale = 10)
    private BigDecimal penaltyExact;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newEntity = true;

    @SuppressWarnings("java:S107")
    public LoanInstallment(UUID loanId, int scheduleVersion, int number, UUID tenantId, LocalDate fromDate,
                           LocalDate dueDate, BigDecimal principalDue, BigDecimal interestDue) {
        this.id = new Key(loanId, scheduleVersion, number);
        this.tenantId = tenantId;
        this.fromDate = fromDate;
        this.dueDate = dueDate;
        this.principalDue = principalDue;
        this.interestDue = interestDue;
        this.penaltyDue = BigDecimal.ZERO;
        this.principalPaid = BigDecimal.ZERO;
        this.interestPaid = BigDecimal.ZERO;
        this.penaltyPaid = BigDecimal.ZERO;
        this.interestWaived = BigDecimal.ZERO;
        this.penaltyExact = BigDecimal.ZERO;
    }

    public int getNumber() {
        return id.number();
    }

    public BigDecimal principalOutstanding() {
        return principalDue.subtract(principalPaid);
    }

    public BigDecimal interestOutstanding() {
        return interestDue.subtract(interestPaid).subtract(interestWaived);
    }

    public BigDecimal penaltyOutstanding() {
        return penaltyDue.subtract(penaltyPaid);
    }

    public boolean isSettled() {
        return principalOutstanding().signum() == 0 && interestOutstanding().signum() == 0
                && penaltyOutstanding().signum() == 0;
    }

    public void pay(BigDecimal principal, BigDecimal interest, BigDecimal penalty, LocalDate on) {
        this.principalPaid = principalPaid.add(principal);
        this.interestPaid = interestPaid.add(interest);
        this.penaltyPaid = penaltyPaid.add(penalty);
        if (isSettled() && paidOn == null) {
            this.paidOn = on;
        }
    }

    /**
     * Adds exact penalty; what falls due is the rounded exact total (cumulative rounding, no drift).
     *
     * @return the penalty that fell due now
     */
    public BigDecimal accruePenalty(BigDecimal exact, int minorUnits, RoundingMode rounding) {
        this.penaltyExact = penaltyExact.add(exact).setScale(10, RoundingMode.HALF_EVEN);
        BigDecimal due = penaltyExact.setScale(minorUnits, rounding);
        if (due.compareTo(penaltyDue) <= 0) {
            return BigDecimal.ZERO.setScale(minorUnits);
        }
        BigDecimal added = due.subtract(penaltyDue);
        this.penaltyDue = due;
        return added;
    }

    /** Interest that will not be collected (e.g. not yet earned when the loan is settled early). */
    public void waiveInterest(BigDecimal amount, LocalDate on) {
        this.interestWaived = interestWaived.add(amount);
        if (isSettled() && paidOn == null) {
            this.paidOn = on;
        }
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
