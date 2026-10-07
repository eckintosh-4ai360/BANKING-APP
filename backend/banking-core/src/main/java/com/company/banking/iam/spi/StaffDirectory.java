package com.company.banking.iam.spi;

import com.company.banking.common.security.BranchScope;

import java.util.Optional;
import java.util.UUID;

/**
 * What IAM needs to know about staff, implemented by the staff module. Keeps the dependency one-directional
 * (staff → iam).
 */
public interface StaffDirectory {

    /**
     * Looks up a staff member of the current tenant.
     */
    Optional<StaffAuthProfile> findAuthProfile(UUID staffId);

    record StaffAuthProfile(UUID staffId, String status, UUID homeBranchId, BranchScope branchScope) {

        public boolean isActive() {
            return "ACTIVE".equals(status);
        }

        public boolean isTerminated() {
            return "TERMINATED".equals(status);
        }
    }
}
