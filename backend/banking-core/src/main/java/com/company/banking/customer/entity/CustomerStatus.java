package com.company.banking.customer.entity;

import java.util.Map;
import java.util.Set;

/**
 * Customer relationship status. {@code PENDING → ACTIVE} happens only through KYC approval and
 * {@code ACTIVE → DORMANT} only through the dormancy job; both are system transitions.
 */
public enum CustomerStatus {
    PENDING,
    ACTIVE,
    DORMANT,
    RESTRICTED,
    FROZEN,
    CLOSED;

    private static final Map<CustomerStatus, Set<CustomerStatus>> ALLOWED = Map.of(
            PENDING, Set.of(ACTIVE, CLOSED),
            ACTIVE, Set.of(RESTRICTED, FROZEN, DORMANT, CLOSED),
            RESTRICTED, Set.of(ACTIVE, FROZEN, CLOSED),
            FROZEN, Set.of(ACTIVE, RESTRICTED),
            DORMANT, Set.of(ACTIVE, CLOSED),
            CLOSED, Set.of());

    public boolean canTransitionTo(CustomerStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
