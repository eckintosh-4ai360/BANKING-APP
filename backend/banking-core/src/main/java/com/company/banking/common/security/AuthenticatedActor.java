package com.company.banking.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * Who is calling, derived exclusively from a verified access token (or the system itself for jobs and seeding).
 *
 * @param tenantId               {@code null} for platform administrators and the platform-level system actor
 * @param passwordChangeRequired signed in with a temporary password; holds no permissions until it is changed
 * @param mfaEnrollmentRequired  must enrol an authenticator before using any permission (policy for platform
 *                               administrators)
 */
public record AuthenticatedActor(
        ActorType type,
        UUID id,
        UUID tenantId,
        String username,
        UUID sessionId,
        Set<String> permissions,
        BranchScope branchScope,
        boolean passwordChangeRequired,
        boolean mfaEnrollmentRequired) {

    public AuthenticatedActor {
        permissions = Set.copyOf(permissions);
    }

    public static AuthenticatedActor system(UUID tenantId) {
        return new AuthenticatedActor(ActorType.SYSTEM, null, tenantId, "system", null,
                Set.of(), BranchScope.all(), false, false);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean isSameUserAs(UUID otherUserId) {
        return id != null && id.equals(otherUserId);
    }

    /**
     * The error code to report when a restricted actor is refused.
     */
    public String restrictionCode() {
        if (passwordChangeRequired) {
            return "PASSWORD_CHANGE_REQUIRED";
        }
        if (mfaEnrollmentRequired) {
            return "MFA_ENROLLMENT_REQUIRED";
        }
        return null;
    }
}
