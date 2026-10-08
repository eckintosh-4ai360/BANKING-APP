package com.company.banking.product.entity;

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
 * The terms of a product at one point in time. Editable while DRAFT; once published the database refuses any change
 * except retiring it, because accounts opened under it are bound by these terms.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "account_product_version")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountProductVersion {

    /**
     * Editable terms of a draft.
     */
    public record Terms(
            String currency,
            UUID depositGlId,
            UUID feeIncomeGlId,
            UUID interestExpenseGlId,
            BigDecimal minOpeningBalance,
            BigDecimal minOperatingBalance,
            BigDecimal maxBalance,
            BigDecimal interestRate,
            String interestCalcMethod,
            String interestPostingFrequency,
            String dayCount,
            int dormancyDays,
            String requiredKycTier,
            boolean allowOverdraft,
            BigDecimal maxOverdraftLimit,
            BigDecimal maxWithdrawalAmount,
            BigDecimal dailyWithdrawalLimit) {
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
    private ProductVersionStatus status;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "deposit_gl_id", nullable = false)
    private UUID depositGlId;

    @Column(name = "fee_income_gl_id")
    private UUID feeIncomeGlId;

    @Column(name = "interest_expense_gl_id")
    private UUID interestExpenseGlId;

    @Column(name = "min_opening_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal minOpeningBalance;

    @Column(name = "min_operating_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal minOperatingBalance;

    @Column(name = "max_balance", precision = 19, scale = 4)
    private BigDecimal maxBalance;

    @Column(name = "interest_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal interestRate;

    @Column(name = "interest_calc_method", nullable = false, length = 25)
    private String interestCalcMethod;

    @Column(name = "interest_posting_frequency", nullable = false, length = 10)
    private String interestPostingFrequency;

    @Column(name = "day_count", nullable = false, length = 12)
    private String dayCount;

    @Column(name = "dormancy_days", nullable = false)
    private int dormancyDays;

    @Column(name = "required_kyc_tier", length = 20)
    private String requiredKycTier;

    @Column(name = "allow_overdraft", nullable = false)
    private boolean allowOverdraft;

    @Column(name = "max_overdraft_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxOverdraftLimit;

    @Column(name = "max_withdrawal_amount", precision = 19, scale = 4)
    private BigDecimal maxWithdrawalAmount;

    @Column(name = "daily_withdrawal_limit", precision = 19, scale = 4)
    private BigDecimal dailyWithdrawalLimit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    public AccountProductVersion(UUID id, UUID tenantId, UUID productId, int versionNo, Terms terms,
                                 Instant createdAt, UUID createdBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.productId = productId;
        this.versionNo = versionNo;
        this.status = ProductVersionStatus.DRAFT;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        apply(terms);
    }

    /**
     * Only drafts change; the database enforces the same for published versions.
     */
    public void apply(Terms terms) {
        if (status != ProductVersionStatus.DRAFT) {
            throw new IllegalStateException("Only a draft version can change");
        }
        this.currency = terms.currency();
        this.depositGlId = terms.depositGlId();
        this.feeIncomeGlId = terms.feeIncomeGlId();
        this.interestExpenseGlId = terms.interestExpenseGlId();
        this.minOpeningBalance = terms.minOpeningBalance();
        this.minOperatingBalance = terms.minOperatingBalance();
        this.maxBalance = terms.maxBalance();
        this.interestRate = terms.interestRate();
        this.interestCalcMethod = terms.interestCalcMethod();
        this.interestPostingFrequency = terms.interestPostingFrequency();
        this.dayCount = terms.dayCount();
        this.dormancyDays = terms.dormancyDays();
        this.requiredKycTier = terms.requiredKycTier();
        this.allowOverdraft = terms.allowOverdraft();
        this.maxOverdraftLimit = terms.maxOverdraftLimit();
        this.maxWithdrawalAmount = terms.maxWithdrawalAmount();
        this.dailyWithdrawalLimit = terms.dailyWithdrawalLimit();
    }

    public Terms terms() {
        return new Terms(currency, depositGlId, feeIncomeGlId, interestExpenseGlId, minOpeningBalance,
                minOperatingBalance, maxBalance, interestRate, interestCalcMethod, interestPostingFrequency, dayCount,
                dormancyDays, requiredKycTier, allowOverdraft, maxOverdraftLimit, maxWithdrawalAmount,
                dailyWithdrawalLimit);
    }

    public void publish(Instant at, UUID by) {
        this.status = ProductVersionStatus.PUBLISHED;
        this.publishedAt = at;
        this.publishedBy = by;
    }

    public void retire() {
        this.status = ProductVersionStatus.RETIRED;
    }
}
