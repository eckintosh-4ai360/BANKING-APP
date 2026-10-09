package com.company.banking.loan.entity;

import com.company.banking.loan.schedule.InterestMethod;
import com.company.banking.loan.schedule.LoanDayCount;
import com.company.banking.loan.schedule.RepaymentFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A disbursed loan with a copy of its terms. Its balances are its three ledger accounts (principal, interest
 * receivable, penalty receivable).
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "loan")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Loan {

    public enum Status { ACTIVE, CLOSED, WRITTEN_OFF }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "loan_number", nullable = false, updatable = false, length = 30)
    private String loanNumber;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "product_version_id", nullable = false, updatable = false)
    private UUID productVersionId;

    @Column(name = "repayment_account_id", nullable = false, updatable = false)
    private UUID repaymentAccountId;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "principal", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal principal;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", nullable = false, updatable = false, length = 40)
    private InterestMethod interestMethod;

    @Column(name = "annual_rate", nullable = false, updatable = false, precision = 9, scale = 6)
    private BigDecimal annualRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_count", nullable = false, updatable = false, length = 12)
    private LoanDayCount dayCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false, updatable = false, length = 10)
    private RepaymentFrequency repaymentFrequency;

    @Column(name = "installments", nullable = false, updatable = false)
    private int installments;

    @Column(name = "principal_grace", nullable = false, updatable = false)
    private int principalGrace;

    @Column(name = "interest_grace", nullable = false, updatable = false)
    private int interestGrace;

    @Column(name = "rounding_mode", nullable = false, updatable = false, length = 10)
    private String roundingMode;

    @Column(name = "allocation_order", nullable = false, updatable = false, length = 40)
    private String allocationOrder;

    @Column(name = "penalty_rate", nullable = false, updatable = false, precision = 9, scale = 6)
    private BigDecimal penaltyRate;

    @Column(name = "penalty_grace_days", nullable = false, updatable = false)
    private int penaltyGraceDays;

    @Column(name = "processing_fee", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal processingFee;

    @Column(name = "principal_ledger_account_id", nullable = false, updatable = false)
    private UUID principalLedgerAccountId;

    @Column(name = "interest_ledger_account_id", nullable = false, updatable = false)
    private UUID interestLedgerAccountId;

    @Column(name = "penalty_ledger_account_id", nullable = false, updatable = false)
    private UUID penaltyLedgerAccountId;

    @Column(name = "interest_income_gl_id", nullable = false, updatable = false)
    private UUID interestIncomeGlId;

    @Column(name = "penalty_income_gl_id", nullable = false, updatable = false)
    private UUID penaltyIncomeGlId;

    @Column(name = "fee_income_gl_id", nullable = false, updatable = false)
    private UUID feeIncomeGlId;

    @Column(name = "disbursement_date", nullable = false, updatable = false)
    private LocalDate disbursementDate;

    @Column(name = "first_due_date", nullable = false, updatable = false)
    private LocalDate firstDueDate;

    @Column(name = "maturity_date", nullable = false, updatable = false)
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status;

    @Column(name = "schedule_version", nullable = false)
    private int scheduleVersion;

    @Column(name = "days_past_due", nullable = false)
    private int daysPastDue;

    @Column(name = "delinquency_band", length = 20)
    private String delinquencyBand;

    @Column(name = "non_accrual", nullable = false)
    private boolean nonAccrual;

    @Column(name = "interest_recognised", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestRecognised;

    @Column(name = "interest_accrued_through")
    private LocalDate interestAccruedThrough;

    @Column(name = "disbursement_transaction_id", nullable = false, updatable = false)
    private UUID disbursementTransactionId;

    @Column(name = "disbursed_by", nullable = false, updatable = false)
    private UUID disbursedBy;

    @Column(name = "disbursed_at", nullable = false, updatable = false)
    private Instant disbursedAt;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "provision_held", nullable = false, precision = 19, scale = 4)
    private BigDecimal provisionHeld;

    @Column(name = "penalty_accrued_through")
    private LocalDate penaltyAccruedThrough;

    @Column(name = "portfolio_processed_through")
    private LocalDate portfolioProcessedThrough;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder
    @SuppressWarnings("java:S107")
    private Loan(UUID id, UUID tenantId, String loanNumber, UUID applicationId, UUID customerId, UUID branchId,
                 UUID productVersionId, UUID repaymentAccountId, String currency, BigDecimal principal,
                 InterestMethod interestMethod, BigDecimal annualRate, LoanDayCount dayCount,
                 RepaymentFrequency repaymentFrequency, int installments, int principalGrace, int interestGrace,
                 String roundingMode, String allocationOrder, BigDecimal penaltyRate, int penaltyGraceDays,
                 BigDecimal processingFee, UUID principalLedgerAccountId, UUID interestLedgerAccountId,
                 UUID penaltyLedgerAccountId, UUID interestIncomeGlId, UUID penaltyIncomeGlId, UUID feeIncomeGlId,
                 LocalDate disbursementDate, LocalDate firstDueDate, LocalDate maturityDate,
                 UUID disbursementTransactionId, UUID disbursedBy, Instant disbursedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.loanNumber = loanNumber;
        this.applicationId = applicationId;
        this.customerId = customerId;
        this.branchId = branchId;
        this.productVersionId = productVersionId;
        this.repaymentAccountId = repaymentAccountId;
        this.currency = currency;
        this.principal = principal;
        this.interestMethod = interestMethod;
        this.annualRate = annualRate;
        this.dayCount = dayCount;
        this.repaymentFrequency = repaymentFrequency;
        this.installments = installments;
        this.principalGrace = principalGrace;
        this.interestGrace = interestGrace;
        this.roundingMode = roundingMode;
        this.allocationOrder = allocationOrder;
        this.penaltyRate = penaltyRate;
        this.penaltyGraceDays = penaltyGraceDays;
        this.processingFee = processingFee;
        this.principalLedgerAccountId = principalLedgerAccountId;
        this.interestLedgerAccountId = interestLedgerAccountId;
        this.penaltyLedgerAccountId = penaltyLedgerAccountId;
        this.interestIncomeGlId = interestIncomeGlId;
        this.penaltyIncomeGlId = penaltyIncomeGlId;
        this.feeIncomeGlId = feeIncomeGlId;
        this.disbursementDate = disbursementDate;
        this.firstDueDate = firstDueDate;
        this.maturityDate = maturityDate;
        this.disbursementTransactionId = disbursementTransactionId;
        this.disbursedBy = disbursedBy;
        this.disbursedAt = disbursedAt;
        this.status = Status.ACTIVE;
        this.scheduleVersion = 1;
        this.interestRecognised = BigDecimal.ZERO;
        this.provisionHeld = BigDecimal.ZERO;
    }

    public void recordAccrual(BigDecimal recognised, LocalDate through) {
        this.interestRecognised = recognised;
        this.interestAccruedThrough = through;
    }

    public void classify(int daysPastDue, String band, boolean nonAccrual) {
        this.daysPastDue = daysPastDue;
        this.delinquencyBand = band;
        this.nonAccrual = nonAccrual;
    }

    public void holdProvision(BigDecimal amount) {
        this.provisionHeld = amount;
    }

    public void penaltiesAccruedThrough(LocalDate date) {
        this.penaltyAccruedThrough = date;
    }

    /** End-of-day classified the loan for this business date (a resumed run skips it). */
    public void processedThrough(LocalDate date) {
        this.portfolioProcessedThrough = date;
    }

    public boolean isProcessedThrough(LocalDate date) {
        return portfolioProcessedThrough != null && !portfolioProcessedThrough.isBefore(date);
    }

    public void close(Status status, LocalDate on) {
        this.status = status;
        this.closedOn = on;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
