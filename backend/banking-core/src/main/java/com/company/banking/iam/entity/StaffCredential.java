package com.company.banking.iam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Login identity of a staff member. Its id is the staff id. Never serialized or audited as a whole.
 */
@Getter
@Entity
@Table(name = "staff_credential")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffCredential implements LoginCredential {

    @Id
    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "username", nullable = false, updatable = false, length = 100)
    private String username;

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

    @Column(name = "login_enabled", nullable = false)
    private boolean loginEnabled;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public StaffCredential(UUID staffId, UUID tenantId, String username, String passwordHash,
                           boolean mustChangePassword, Instant now) {
        this.staffId = staffId;
        this.tenantId = tenantId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.passwordChangedAt = now;
        this.mustChangePassword = mustChangePassword;
        this.loginEnabled = true;
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

    public void setLoginEnabled(boolean loginEnabled) {
        this.loginEnabled = loginEnabled;
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
