package com.company.banking.iam.dto;

import java.time.Instant;

/**
 * Non-secret login state of a staff member, for administration screens.
 */
public record CredentialInfo(
        String username,
        boolean loginEnabled,
        boolean mustChangePassword,
        boolean locked,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant passwordChangedAt) {
}
