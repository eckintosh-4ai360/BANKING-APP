package com.company.banking.channel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A customer's digital banking credential: sign-in name (their phone number), password and transaction PIN, both
 * stored only as hashes, with the failed-attempt counters that lock them.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "customer_credential")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerCredential {

    public enum Status { ACTIVE, DISABLED }

    @Id
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "username", nullable = false, length = 20)
    private String username;

    @Getter(AccessLevel.NONE)
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Getter(AccessLevel.NONE)
    @Column(name = "pin_hash", nullable = false)
    private String pinHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "disabled_reason", length = 300)
    private String disabledReason;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "pin_failed_attempts", nullable = false)
    private int pinFailedAttempts;

    @Column(name = "pin_locked", nullable = false)
    private boolean pinLocked;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Text alerts for money in and out (security notices are always texted). */
    @Column(name = "sms_alerts", nullable = false)
    private boolean smsAlerts;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @Column(name = "pin_changed_at", nullable = false)
    private Instant pinChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public CustomerCredential(UUID customerId, UUID tenantId, String username, String passwordHash, String pinHash,
                              Instant now) {
        this.customerId = customerId;
        this.tenantId = tenantId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.pinHash = pinHash;
        this.status = Status.ACTIVE;
        this.smsAlerts = true;
        this.passwordChangedAt = now;
        this.pinChangedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String pinHash() {
        return pinHash;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * @return true when this failure locked the sign-in
     */
    public boolean registerFailedLogin(int maxAttempts, Duration lockout, Instant now) {
        this.failedAttempts++;
        this.updatedAt = now;
        if (failedAttempts >= maxAttempts) {
            this.lockedUntil = now.plus(lockout);
            this.failedAttempts = 0;
            return true;
        }
        return false;
    }

    /**
     * The password was right (the sign-in may still need a device check).
     */
    public void resetFailedLogins(Instant now) {
        if (failedAttempts != 0 || lockedUntil != null) {
            this.failedAttempts = 0;
            this.lockedUntil = null;
            this.updatedAt = now;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    public void changePassword(String newHash, Instant now) {
        this.passwordHash = newHash;
        this.passwordChangedAt = now;
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    /**
     * Sets a new PIN and unlocks it.
     */
    public void changePin(String newHash, Instant now) {
        this.pinHash = newHash;
        this.pinChangedAt = now;
        this.pinFailedAttempts = 0;
        this.pinLocked = false;
        this.updatedAt = now;
    }

    /**
     * @return true when this failure locked the PIN (it then needs a reset)
     */
    public boolean registerWrongPin(int maxAttempts, Instant now) {
        this.pinFailedAttempts++;
        this.updatedAt = now;
        if (pinFailedAttempts >= maxAttempts) {
            this.pinLocked = true;
            return true;
        }
        return false;
    }

    public void registerRightPin(Instant now) {
        if (pinFailedAttempts != 0) {
            this.pinFailedAttempts = 0;
            this.updatedAt = now;
        }
    }

    public void chooseSmsAlerts(boolean enabled, Instant now) {
        this.smsAlerts = enabled;
        this.updatedAt = now;
    }

    public void disable(String reason, Instant now) {
        this.status = Status.DISABLED;
        this.disabledReason = reason;
        this.updatedAt = now;
    }

    public void enable(Instant now) {
        this.status = Status.ACTIVE;
        this.disabledReason = null;
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    @Override
    public String toString() {
        return "CustomerCredential[customerId=" + customerId + ", status=" + status + "]";
    }
}
