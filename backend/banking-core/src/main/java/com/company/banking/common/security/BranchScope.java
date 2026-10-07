package com.company.banking.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * The branches whose data an actor may see and act on.
 */
public record BranchScope(boolean allBranches, Set<UUID> branchIds) {

    private static final BranchScope ALL = new BranchScope(true, Set.of());
    private static final BranchScope NONE = new BranchScope(false, Set.of());

    public BranchScope {
        branchIds = allBranches ? Set.of() : Set.copyOf(branchIds);
    }

    public static BranchScope all() {
        return ALL;
    }

    public static BranchScope none() {
        return NONE;
    }

    public static BranchScope of(Set<UUID> branchIds) {
        return new BranchScope(false, branchIds);
    }

    public boolean permits(UUID branchId) {
        return allBranches || (branchId != null && branchIds.contains(branchId));
    }
}
