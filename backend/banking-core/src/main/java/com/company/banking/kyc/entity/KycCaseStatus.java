package com.company.banking.kyc.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum KycCaseStatus {
    OPEN,
    PENDING_REVIEW,
    RETURNED,
    APPROVED,
    REJECTED,
    CANCELLED;

    public static final Set<KycCaseStatus> UNDECIDED = EnumSet.of(OPEN, PENDING_REVIEW, RETURNED);

    private static final Map<KycCaseStatus, Set<KycCaseStatus>> ALLOWED = Map.of(
            OPEN, Set.of(PENDING_REVIEW, CANCELLED),
            RETURNED, Set.of(PENDING_REVIEW, CANCELLED),
            PENDING_REVIEW, Set.of(RETURNED, APPROVED, REJECTED),
            APPROVED, Set.of(),
            REJECTED, Set.of(),
            CANCELLED, Set.of());

    public boolean canTransitionTo(KycCaseStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /**
     * The officer is still collecting evidence.
     */
    public boolean isCapturing() {
        return this == OPEN || this == RETURNED;
    }
}
