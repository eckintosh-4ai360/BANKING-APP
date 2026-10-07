package com.company.banking.tenant.entity;

import java.util.Map;
import java.util.Set;

public enum TenantStatus {
    ONBOARDING,
    ACTIVE,
    SUSPENDED,
    TERMINATED;

    private static final Map<TenantStatus, Set<TenantStatus>> ALLOWED = Map.of(
            ONBOARDING, Set.of(ACTIVE, TERMINATED),
            ACTIVE, Set.of(SUSPENDED, TERMINATED),
            SUSPENDED, Set.of(ACTIVE, TERMINATED),
            TERMINATED, Set.of());

    public boolean canTransitionTo(TenantStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
