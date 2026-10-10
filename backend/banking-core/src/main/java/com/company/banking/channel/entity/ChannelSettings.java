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
 * The institution's limits for transfers from the customer app, in its base currency.
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
}
