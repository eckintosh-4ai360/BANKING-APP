package com.company.banking.iam.entity;

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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A platform (super) administrator. Separate identity store from tenant staff.
 */
@Getter
@Entity
@Table(name = "platform_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformUser extends AuditableEntity implements LoginCredential {

    public enum Status { ACTIVE, DISABLED }

    @Id
    private UUID id;

    @Column(name = "username", nullable = false, updatable = false, length = 100)
    private String username;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false, length = 30)
    private PlatformRole platformRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "mfa_secret_encrypted", length = 512)
    private String mfaSecretEncrypted;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "mfa_enrolled_at")
    private Instant mfaEnrolledAt;

    @Column(name = "mfa_last_used_step")
    private Long mfaLastUsedStep;

    public PlatformUser(UUID id, String username, String email, String fullName, PlatformRole platformRole,
                        String passwordHash, boolean mustChangePassword, Instant now) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.platformRole = platformRole;
        this.status = Status.ACTIVE;
        this.passwordHash = passwordHash;
        this.passwordChangedAt = now;
        this.mustChangePassword = mustChangePassword;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    @Override
    public boolean registerFailedAttempt(int maxAttempts, Duration lockDuration, Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            failedLoginAttempts = 0;
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    @Override
    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    @Override
    public void changePassword(String newHash, boolean mustChangePassword, Instant now) {
        this.passwordHash = newHash;
        this.mustChangePassword = mustChangePassword;
        this.passwordChangedAt = now;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    @Override
    public void startMfaEnrollment(String encryptedSecret) {
        if (mfaEnabled) {
            throw new IllegalStateException("MFA is already enabled");
        }
        this.mfaSecretEncrypted = encryptedSecret;
        this.mfaLastUsedStep = null;
    }

    @Override
    public void enableMfa(long acceptedStep, Instant now) {
        this.mfaEnabled = true;
        this.mfaEnrolledAt = now;
        this.mfaLastUsedStep = acceptedStep;
    }

    @Override
    public void recordMfaUse(long acceptedStep) {
        this.mfaLastUsedStep = acceptedStep;
    }

    @Override
    public void resetMfa() {
        this.mfaEnabled = false;
        this.mfaSecretEncrypted = null;
        this.mfaEnrolledAt = null;
        this.mfaLastUsedStep = null;
    }
}
