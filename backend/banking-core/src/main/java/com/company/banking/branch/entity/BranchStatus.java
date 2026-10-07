package com.company.banking.branch.entity;

import java.util.Map;
import java.util.Set;

public enum BranchStatus {
    ACTIVE,
    INACTIVE,
    CLOSED;

    private static final Map<BranchStatus, Set<BranchStatus>> ALLOWED = Map.of(
            ACTIVE, Set.of(INACTIVE, CLOSED),
            INACTIVE, Set.of(ACTIVE, CLOSED),
            CLOSED, Set.of());

    public boolean canTransitionTo(BranchStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
