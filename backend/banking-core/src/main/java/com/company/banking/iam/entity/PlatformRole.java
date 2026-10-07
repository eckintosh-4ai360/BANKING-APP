package com.company.banking.iam.entity;

import com.company.banking.common.security.Permissions;

import java.util.Set;

/**
 * Platform roles are few and fixed, so they are defined in code rather than configured.
 */
public enum PlatformRole {

    PLATFORM_OWNER(Set.of(Permissions.PLATFORM_TENANT_VIEW, Permissions.PLATFORM_TENANT_MANAGE,
            Permissions.PLATFORM_FEATURE_MANAGE, Permissions.PLATFORM_AUDIT_VIEW, Permissions.PLATFORM_USER_MANAGE)),
    PLATFORM_ADMIN(Set.of(Permissions.PLATFORM_TENANT_VIEW, Permissions.PLATFORM_TENANT_MANAGE,
            Permissions.PLATFORM_FEATURE_MANAGE, Permissions.PLATFORM_AUDIT_VIEW)),
    PLATFORM_SUPPORT(Set.of(Permissions.PLATFORM_TENANT_VIEW, Permissions.PLATFORM_AUDIT_VIEW));

    private final Set<String> permissions;

    PlatformRole(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Set<String> permissions() {
        return permissions;
    }
}
