package com.company.banking.staff.entity;

import java.util.Map;
import java.util.Set;

public enum StaffStatus {
    ACTIVE,
    SUSPENDED,
    TERMINATED;

    private static final Map<StaffStatus, Set<StaffStatus>> ALLOWED = Map.of(
            ACTIVE, Set.of(SUSPENDED, TERMINATED),
            SUSPENDED, Set.of(ACTIVE, TERMINATED),
            TERMINATED, Set.of());

    public boolean canTransitionTo(StaffStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
