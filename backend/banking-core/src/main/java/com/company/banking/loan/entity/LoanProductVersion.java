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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * The terms of a loan product at one point in time. Editable while a draft; once published it never changes
 * (database trigger) and is retired when a newer version is published.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "loan_product_version")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanProductVersion {

    public enum Status { DRAFT, PUBLISHED, RETIRED }

    /** Everything a version's terms consist of. */
    public record Terms(String currency, BigDecimal minAmount, BigDecimal maxAmount, int minInstallments,
                        int maxInstallments, InterestMethod interestMethod, BigDecimal annualRate,
                        LoanDayCount dayCount, RepaymentFrequency repaymentFrequency, int principalGrace,
                        int interestGrace, String roundingMode, String allocationOrder, BigDecimal processingFeeRate,
                        BigDecimal processingFeeFlat, BigDecimal penaltyRate, int penaltyGraceDays,
                        int requiredGuarantors, BigDecimal collateralCoverage, BigDecimal secondApprovalAbove,
                        String requiredKycTier, UUID principalGlId, UUID interestReceivableGlId,
                        UUID interestIncomeGlId, UUID feeIncomeGlId, UUID penaltyReceivableGlId,
                        UUID penaltyIncomeGlId) {
    }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "min_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "min_installments", nullable = false)
    private int minInstallments;

    @Column(name = "max_installments", nullable = false)
    private int maxInstallments;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", nullable = false, length = 40)
    private InterestMethod interestMethod;

    @Column(name = "annual_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal annualRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_count", nullable = false, length = 12)
    private LoanDayCount dayCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false, length = 10)
    private RepaymentFrequency repaymentFrequency;

    @Column(name = "principal_grace", nullable = false)
    private int principalGrace;

    @Column(name = "interest_grace", nullable = false)
    private int interestGrace;

    @Column(name = "rounding_mode", nullable = false, length = 10)
    private String roundingMode;

    @Column(name = "allocation_order", nullable = false, length = 40)
    private String allocationOrder;

    @Column(name = "processing_fee_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal processingFeeRate;

    @Column(name = "processing_fee_flat", nullable = false, precision = 19, scale = 4)
    private BigDecimal processingFeeFlat;

    @Column(name = "penalty_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal penaltyRate;

    @Column(name = "penalty_grace_days", nullable = false)
    private int penaltyGraceDays;

    @Column(name = "required_guarantors", nullable = false)
    private int requiredGuarantors;

    @Column(name = "collateral_coverage", nullable = false, precision = 9, scale = 4)
    private BigDecimal collateralCoverage;

    @Column(name = "second_approval_above", precision = 19, scale = 4)
    private BigDecimal secondApprovalAbove;

    @Column(name = "required_kyc_tier", length = 20)
    private String requiredKycTier;

    @Column(name = "principal_gl_id", nullable = false)
    private UUID principalGlId;

    @Column(name = "interest_receivable_gl_id", nullable = false)
    private UUID interestReceivableGlId;

    @Column(name = "interest_income_gl_id", nullable = false)
    private UUID interestIncomeGlId;

    @Column(name = "fee_income_gl_id", nullable = false)
    private UUID feeIncomeGlId;

    @Column(name = "penalty_receivable_gl_id", nullable = false)
    private UUID penaltyReceivableGlId;

    @Column(name = "penalty_income_gl_id", nullable = false)
    private UUID penaltyIncomeGlId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public LoanProductVersion(UUID id, UUID tenantId, UUID productId, int versionNo, Terms terms, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.productId = productId;
        this.versionNo = versionNo;
        this.status = Status.DRAFT;
        this.createdAt = now;
        apply(terms);
    }

    public void apply(Terms terms) {
        this.currency = terms.currency();
        this.minAmount = terms.minAmount();
        this.maxAmount = terms.maxAmount();
        this.minInstallments = terms.minInstallments();
        this.maxInstallments = terms.maxInstallments();
        this.interestMethod = terms.interestMethod();
        this.annualRate = terms.annualRate();
        this.dayCount = terms.dayCount();
        this.repaymentFrequency = terms.repaymentFrequency();
        this.principalGrace = terms.principalGrace();
        this.interestGrace = terms.interestGrace();
        this.roundingMode = terms.roundingMode();
        this.allocationOrder = terms.allocationOrder();
        this.processingFeeRate = terms.processingFeeRate();
        this.processingFeeFlat = terms.processingFeeFlat();
        this.penaltyRate = terms.penaltyRate();
        this.penaltyGraceDays = terms.penaltyGraceDays();
        this.requiredGuarantors = terms.requiredGuarantors();
        this.collateralCoverage = terms.collateralCoverage();
        this.secondApprovalAbove = terms.secondApprovalAbove();
        this.requiredKycTier = terms.requiredKycTier();
        this.principalGlId = terms.principalGlId();
        this.interestReceivableGlId = terms.interestReceivableGlId();
        this.interestIncomeGlId = terms.interestIncomeGlId();
        this.feeIncomeGlId = terms.feeIncomeGlId();
        this.penaltyReceivableGlId = terms.penaltyReceivableGlId();
        this.penaltyIncomeGlId = terms.penaltyIncomeGlId();
    }

    public Terms terms() {
        return new Terms(currency, minAmount, maxAmount, minInstallments, maxInstallments, interestMethod, annualRate,
                dayCount, repaymentFrequency, principalGrace, interestGrace, roundingMode, allocationOrder,
                processingFeeRate, processingFeeFlat, penaltyRate, penaltyGraceDays, requiredGuarantors,
                collateralCoverage, secondApprovalAbove, requiredKycTier, principalGlId, interestReceivableGlId,
                interestIncomeGlId, feeIncomeGlId, penaltyReceivableGlId, penaltyIncomeGlId);
    }

    public void publish(Instant at) {
        this.status = Status.PUBLISHED;
        this.publishedAt = at;
    }

    public void retire() {
        this.status = Status.RETIRED;
    }
}
