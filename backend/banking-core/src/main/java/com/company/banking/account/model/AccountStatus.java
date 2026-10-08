package com.company.banking.account.model;

import java.util.Map;
import java.util.Set;

/**
 * Account lifecycle. PENDING accounts wait for their opening deposit; RESTRICTED and DORMANT accounts accept money
 * but pay nothing out; FROZEN accounts do neither; CLOSED is final.
 */
public enum AccountStatus {
    PENDING,
    ACTIVE,
    RESTRICTED,
    FROZEN,
    DORMANT,
    CLOSED;

    private static final Map<AccountStatus, Set<AccountStatus>> ALLOWED = Map.of(
            PENDING, Set.of(ACTIVE, CLOSED),
            ACTIVE, Set.of(RESTRICTED, FROZEN, DORMANT, CLOSED),
            RESTRICTED, Set.of(ACTIVE, FROZEN, CLOSED),
            FROZEN, Set.of(ACTIVE, RESTRICTED),
            DORMANT, Set.of(ACTIVE, FROZEN, CLOSED),
            CLOSED, Set.of());

    public boolean canTransitionTo(AccountStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean allowsCredit() {
        return this == PENDING || this == ACTIVE || this == RESTRICTED || this == DORMANT;
    }

    public boolean allowsDebit() {
        return this == ACTIVE;
    }
}
