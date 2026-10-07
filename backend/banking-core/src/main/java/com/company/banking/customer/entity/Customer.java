package com.company.banking.customer.entity;

import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Customer aggregate root. Profile, addresses, identifications and related records hang off its id.
 */
@Getter
@Entity
@Table(name = "customer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "customer_number", nullable = false, updatable = false, length = 20)
    private String customerNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, updatable = false, length = 20)
    private CustomerType customerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerStatus status;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus;

    @Column(name = "kyc_tier_code", length = 30)
    private String kycTierCode;

    @Column(name = "kyc_verified_at")
    private Instant kycVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "primary_phone", length = 20)
    private String primaryPhone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "home_branch_id", nullable = false)
    private UUID homeBranchId;

    @Column(name = "relationship_officer_id")
    private UUID relationshipOfficerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_channel", nullable = false, updatable = false, length = 20)
    private OnboardingChannel onboardingChannel;

    @Column(name = "preferred_language", length = 10)
    private String preferredLanguage;

    @Column(name = "profile_updated_at", nullable = false)
    private Instant profileUpdatedAt;

    public Customer(UUID id, UUID tenantId, String customerNumber, CustomerType customerType, String displayName,
                    UUID homeBranchId, OnboardingChannel onboardingChannel, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerNumber = customerNumber;
        this.customerType = customerType;
        this.displayName = displayName;
        this.homeBranchId = homeBranchId;
        this.onboardingChannel = onboardingChannel;
        this.status = CustomerStatus.PENDING;
        this.kycStatus = KycStatus.NOT_STARTED;
        this.riskLevel = RiskLevel.UNASSESSED;
        this.profileUpdatedAt = now;
    }

    public void updateContact(String primaryPhone, String email, String preferredLanguage,
                              UUID relationshipOfficerId) {
        this.primaryPhone = primaryPhone;
        this.email = email;
        this.preferredLanguage = preferredLanguage;
        this.relationshipOfficerId = relationshipOfficerId;
    }

    /**
     * Records that profile data changed. Also bumps the optimistic-lock version, so profile edits made through
     * child tables are detected as concurrent modifications of the customer.
     */
    public void markProfileUpdated(String displayName, Instant now) {
        this.displayName = displayName;
        this.profileUpdatedAt = now;
    }

    public void changeStatus(CustomerStatus target, String reason) {
        if (!status.canTransitionTo(target)) {
            throw new BankingException(CommonErrorCode.INVALID_STATE_TRANSITION,
                    "Customer cannot move from " + status + " to " + target + ".");
        }
        if (target == CustomerStatus.ACTIVE && kycVerifiedAt == null) {
            throw new BankingException(CommonErrorCode.INVALID_STATE_TRANSITION,
                    "A customer can only become active after KYC verification.");
        }
        this.status = target;
        this.statusReason = reason;
    }

    // ---------------------------------------------------------------- KYC lifecycle (driven by the KYC module)

    public void kycStarted() {
        requireKycStatus(KycStatus.NOT_STARTED, KycStatus.REJECTED, KycStatus.EXPIRED, KycStatus.VERIFIED);
        this.kycStatus = KycStatus.IN_PROGRESS;
    }

    public void kycSubmitted() {
        requireKycStatus(KycStatus.IN_PROGRESS);
        this.kycStatus = KycStatus.PENDING_REVIEW;
    }

    public void kycReturned() {
        requireKycStatus(KycStatus.PENDING_REVIEW);
        this.kycStatus = KycStatus.IN_PROGRESS;
    }

    public void kycApproved(String tierCode, RiskLevel assessedRisk, Instant now) {
        requireKycStatus(KycStatus.PENDING_REVIEW);
        this.kycStatus = KycStatus.VERIFIED;
        this.kycTierCode = tierCode;
        this.riskLevel = assessedRisk;
        this.kycVerifiedAt = now;
        if (status == CustomerStatus.PENDING) {
            this.status = CustomerStatus.ACTIVE;
            this.statusReason = "KYC approved";
        }
    }

    public void kycRejected() {
        requireKycStatus(KycStatus.PENDING_REVIEW);
        this.kycStatus = KycStatus.REJECTED;
    }

    /**
     * A withdrawn case leaves the customer where they were before it opened.
     */
    public void kycCancelled() {
        requireKycStatus(KycStatus.IN_PROGRESS);
        this.kycStatus = kycVerifiedAt != null ? KycStatus.VERIFIED : KycStatus.NOT_STARTED;
    }

    public boolean isKycUnderReview() {
        return kycStatus == KycStatus.PENDING_REVIEW;
    }

    /**
     * Verified identity data may only change through a KYC update case (which moves KYC back to in-progress).
     */
    public boolean isIdentityDataLocked() {
        return kycStatus == KycStatus.VERIFIED || kycStatus == KycStatus.PENDING_REVIEW;
    }

    private void requireKycStatus(KycStatus... allowed) {
        for (KycStatus candidate : allowed) {
            if (kycStatus == candidate) {
                return;
            }
        }
        throw new BankingException(CommonErrorCode.INVALID_STATE_TRANSITION,
                "The customer's KYC status " + kycStatus + " does not allow this step.");
    }
}
