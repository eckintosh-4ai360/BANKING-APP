package com.company.banking.iam.entity;

import java.time.Duration;
import java.time.Instant;

/**
 * Password, lockout and failed-attempt state shared by staff credentials and platform users.
 */
public interface LoginCredential {

    String getUsername();

    String getPasswordHash();

    boolean isMustChangePassword();

    Instant getLockedUntil();

    default boolean isLocked(Instant now) {
        return getLockedUntil() != null && getLockedUntil().isAfter(now);
    }

    /**
     * @return {@code true} if this failure locked the account
     */
    boolean registerFailedAttempt(int maxAttempts, Duration lockDuration, Instant now);

    void registerSuccessfulLogin(Instant now);

    void changePassword(String newHash, boolean mustChangePassword, Instant now);

    // ------------------------------------------------------------------------------------------- TOTP MFA

    String getMfaSecretEncrypted();

    boolean isMfaEnabled();

    Long getMfaLastUsedStep();

    /**
     * Stores a new, not yet confirmed secret (replacing an unconfirmed one).
     */
    void startMfaEnrollment(String encryptedSecret);

    void enableMfa(long acceptedStep, Instant now);

    void recordMfaUse(long acceptedStep);

    /**
     * Removes the enrollment, e.g. when an administrator resets a lost authenticator.
     */
    void resetMfa();
}
