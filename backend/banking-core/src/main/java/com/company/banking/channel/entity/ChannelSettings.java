package com.company.banking.channel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * The institution's settings for the customer app: limits for transfers, in its base currency, and whether people
 * who are not yet customers can sign up in the app (and with which branch, KYC tier and first account).
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "customer_channel_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelSettings {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "max_transfer_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxTransferAmount;

    @Column(name = "daily_transfer_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyTransferLimit;

    @Column(name = "beneficiary_cooldown_hours", nullable = false)
    private int beneficiaryCooldownHours;

    @Column(name = "cooldown_max_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal cooldownMaxAmount;

    @Column(name = "new_device_cooldown_hours", nullable = false)
    private int newDeviceCooldownHours;

    @Column(name = "new_device_max_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal newDeviceMaxAmount;

    @Column(name = "self_onboarding_enabled", nullable = false)
    private boolean selfOnboardingEnabled;

    @Column(name = "onboarding_branch_id")
    private UUID onboardingBranchId;

    @Column(name = "onboarding_tier_code", length = 30)
    private String onboardingTierCode;

    @Column(name = "onboarding_product_id")
    private UUID onboardingProductId;

    @Column(name = "onboarding_min_age", nullable = false)
    private int onboardingMinAge = 18;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @SuppressWarnings("java:S107")
    public ChannelSettings(UUID tenantId, BigDecimal maxTransferAmount, BigDecimal dailyTransferLimit,
                           int beneficiaryCooldownHours, BigDecimal cooldownMaxAmount, int newDeviceCooldownHours,
                           BigDecimal newDeviceMaxAmount, Instant now) {
        this.tenantId = tenantId;
        update(maxTransferAmount, dailyTransferLimit, beneficiaryCooldownHours, cooldownMaxAmount,
                newDeviceCooldownHours, newDeviceMaxAmount, now, null);
    }

    @SuppressWarnings("java:S107")
    public void update(BigDecimal maxTransferAmount, BigDecimal dailyTransferLimit, int beneficiaryCooldownHours,
                       BigDecimal cooldownMaxAmount, int newDeviceCooldownHours, BigDecimal newDeviceMaxAmount,
                       Instant now, UUID by) {
        this.maxTransferAmount = maxTransferAmount;
        this.dailyTransferLimit = dailyTransferLimit;
        this.beneficiaryCooldownHours = beneficiaryCooldownHours;
        this.cooldownMaxAmount = cooldownMaxAmount;
        this.newDeviceCooldownHours = newDeviceCooldownHours;
        this.newDeviceMaxAmount = newDeviceMaxAmount;
        this.updatedAt = now;
        this.updatedBy = by;
    }

    /**
     * Sign-up in the app. Turning it on needs the branch new customers belong to, the KYC tier they are verified to
     * and the product of their first account; turning it off keeps them for next time.
     */
    @SuppressWarnings("java:S107")
    public void updateOnboarding(boolean enabled, UUID branchId, String tierCode, UUID productId, int minAge,
                                 Instant now, UUID by) {
        if (enabled && (branchId == null || tierCode == null || productId == null)) {
            throw new IllegalArgumentException("Sign-up needs a branch, a KYC tier and a product");
        }
        this.selfOnboardingEnabled = enabled;
        this.onboardingBranchId = branchId;
        this.onboardingTierCode = tierCode;
        this.onboardingProductId = productId;
        this.onboardingMinAge = minAge;
        this.updatedAt = now;
        this.updatedBy = by;
    }
}
