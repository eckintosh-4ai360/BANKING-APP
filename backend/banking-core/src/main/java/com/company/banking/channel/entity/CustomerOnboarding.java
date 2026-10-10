package com.company.banking.channel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A customer who signed up in the app: their risk profile answers, the KYC case they submitted and the first account
 * they opened. The rest of what they captured lives on the customer record like any other customer's.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "customer_onboarding")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOnboarding {

    @Id
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "source_of_funds", length = 30)
    private String sourceOfFunds;

    @Column(name = "account_purpose", length = 30)
    private String accountPurpose;

    @Column(name = "expected_monthly_turnover", length = 30)
    private String expectedMonthlyTurnover;

    @Column(name = "politically_exposed")
    private Boolean politicallyExposed;

    @Column(name = "risk_answered_at")
    private Instant riskAnsweredAt;

    @Column(name = "kyc_case_id")
    private UUID kycCaseId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public CustomerOnboarding(UUID customerId, UUID tenantId, Instant now) {
        this.customerId = customerId;
        this.tenantId = tenantId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean hasRiskProfile() {
        return riskAnsweredAt != null;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public void answerRiskProfile(String sourceOfFunds, String accountPurpose, String expectedMonthlyTurnover,
                                  boolean politicallyExposed, Instant now) {
        this.sourceOfFunds = sourceOfFunds;
        this.accountPurpose = accountPurpose;
        this.expectedMonthlyTurnover = expectedMonthlyTurnover;
        this.politicallyExposed = politicallyExposed;
        this.riskAnsweredAt = now;
        this.updatedAt = now;
    }

    public void caseOpened(UUID kycCaseId, Instant now) {
        this.kycCaseId = kycCaseId;
        this.updatedAt = now;
    }

    public void submitted(Instant now) {
        this.submittedAt = now;
        this.updatedAt = now;
    }

    public void completed(UUID accountId, Instant now) {
        this.accountId = accountId;
        this.completedAt = now;
        this.updatedAt = now;
    }
}
