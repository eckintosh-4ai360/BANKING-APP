package com.company.banking.loan.entity;

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
 * A request for a loan and where it is in its workflow: DRAFT → SUBMITTED → ASSESSED → RECOMMENDED → APPROVED →
 * DISBURSED, or REJECTED / WITHDRAWN on the way.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "loan_application")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanApplication {

    public enum Status { DRAFT, SUBMITTED, ASSESSED, RECOMMENDED, APPROVED, REJECTED, WITHDRAWN, DISBURSED }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "application_number", nullable = false, updatable = false, length = 30)
    private String applicationNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "product_version_id", nullable = false, updatable = false)
    private UUID productVersionId;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal requestedAmount;

    @Column(name = "requested_installments", nullable = false)
    private int requestedInstallments;

    @Column(name = "purpose", nullable = false, length = 300)
    private String purpose;

    @Column(name = "monthly_income", precision = 19, scale = 4)
    private BigDecimal monthlyIncome;

    @Column(name = "monthly_expenses", precision = 19, scale = 4)
    private BigDecimal monthlyExpenses;

    @Column(name = "existing_debt", precision = 19, scale = 4)
    private BigDecimal existingDebt;

    @Column(name = "disbursement_account_id", nullable = false)
    private UUID disbursementAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status;

    @Column(name = "risk_rating", length = 10)
    private String riskRating;

    @Column(name = "assessment_note", length = 1000)
    private String assessmentNote;

    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Column(name = "approved_installments")
    private Integer approvedInstallments;

    @Column(name = "first_due_date")
    private LocalDate firstDueDate;

    @Column(name = "loan_officer_id", nullable = false, updatable = false)
    private UUID loanOfficerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder
    @SuppressWarnings("java:S107")
    private LoanApplication(UUID id, UUID tenantId, String applicationNumber, UUID customerId, UUID branchId,
                            UUID productId, UUID productVersionId, String currency, BigDecimal requestedAmount,
                            int requestedInstallments, String purpose, BigDecimal monthlyIncome,
                            BigDecimal monthlyExpenses, BigDecimal existingDebt, UUID disbursementAccountId,
                            UUID loanOfficerId, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.applicationNumber = applicationNumber;
        this.customerId = customerId;
        this.branchId = branchId;
        this.productId = productId;
        this.productVersionId = productVersionId;
        this.currency = currency;
        this.requestedAmount = requestedAmount;
        this.requestedInstallments = requestedInstallments;
        this.purpose = purpose;
        this.monthlyIncome = monthlyIncome;
        this.monthlyExpenses = monthlyExpenses;
        this.existingDebt = existingDebt;
        this.disbursementAccountId = disbursementAccountId;
        this.loanOfficerId = loanOfficerId;
        this.status = Status.DRAFT;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void edit(BigDecimal requestedAmount, int requestedInstallments, String purpose, BigDecimal monthlyIncome,
                     BigDecimal monthlyExpenses, BigDecimal existingDebt, UUID disbursementAccountId, Instant now) {
        this.requestedAmount = requestedAmount;
        this.requestedInstallments = requestedInstallments;
        this.purpose = purpose;
        this.monthlyIncome = monthlyIncome;
        this.monthlyExpenses = monthlyExpenses;
        this.existingDebt = existingDebt;
        this.disbursementAccountId = disbursementAccountId;
        this.updatedAt = now;
    }

    public void assess(String riskRating, String note, Instant now) {
        this.riskRating = riskRating;
        this.assessmentNote = note;
        this.status = Status.ASSESSED;
        this.updatedAt = now;
    }

    public void approveTerms(BigDecimal amount, int installments, LocalDate firstDueDate, Instant now) {
        this.approvedAmount = amount;
        this.approvedInstallments = installments;
        this.firstDueDate = firstDueDate;
        this.updatedAt = now;
    }

    public void moveTo(Status status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }
}
