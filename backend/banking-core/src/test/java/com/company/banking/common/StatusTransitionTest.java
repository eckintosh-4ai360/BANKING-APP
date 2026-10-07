package com.company.banking.common;

import com.company.banking.branch.entity.BranchStatus;
import com.company.banking.customer.entity.CustomerStatus;
import com.company.banking.kyc.entity.KycCaseStatus;
import com.company.banking.staff.entity.StaffStatus;
import com.company.banking.tenant.entity.TenantStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusTransitionTest {

    @Test
    void terminatedAndClosedStatesAreFinal() {
        for (TenantStatus target : TenantStatus.values()) {
            assertThat(TenantStatus.TERMINATED.canTransitionTo(target)).isFalse();
        }
        for (StaffStatus target : StaffStatus.values()) {
            assertThat(StaffStatus.TERMINATED.canTransitionTo(target)).isFalse();
        }
        for (BranchStatus target : BranchStatus.values()) {
            assertThat(BranchStatus.CLOSED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void suspensionIsReversible() {
        assertThat(TenantStatus.ACTIVE.canTransitionTo(TenantStatus.SUSPENDED)).isTrue();
        assertThat(TenantStatus.SUSPENDED.canTransitionTo(TenantStatus.ACTIVE)).isTrue();
        assertThat(StaffStatus.ACTIVE.canTransitionTo(StaffStatus.SUSPENDED)).isTrue();
        assertThat(StaffStatus.SUSPENDED.canTransitionTo(StaffStatus.ACTIVE)).isTrue();
        assertThat(BranchStatus.ACTIVE.canTransitionTo(BranchStatus.INACTIVE)).isTrue();
        assertThat(BranchStatus.INACTIVE.canTransitionTo(BranchStatus.ACTIVE)).isTrue();
    }

    @Test
    void customersBecomeActiveOnlyFromOnboardingOrRestrictionsAndFrozenOnesCannotClose() {
        assertThat(CustomerStatus.PENDING.canTransitionTo(CustomerStatus.ACTIVE)).isTrue();
        assertThat(CustomerStatus.PENDING.canTransitionTo(CustomerStatus.FROZEN)).isFalse();
        assertThat(CustomerStatus.FROZEN.canTransitionTo(CustomerStatus.CLOSED)).isFalse();
        assertThat(CustomerStatus.FROZEN.canTransitionTo(CustomerStatus.ACTIVE)).isTrue();
        for (CustomerStatus target : CustomerStatus.values()) {
            assertThat(CustomerStatus.CLOSED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void kycCasesAreDecidedOnlyAfterSubmission() {
        assertThat(KycCaseStatus.OPEN.canTransitionTo(KycCaseStatus.APPROVED)).isFalse();
        assertThat(KycCaseStatus.PENDING_REVIEW.canTransitionTo(KycCaseStatus.APPROVED)).isTrue();
        assertThat(KycCaseStatus.APPROVED.canTransitionTo(KycCaseStatus.RETURNED)).isFalse();
    }

    @Test
    void onboardingTenantsCannotBeSuspendedBeforeActivation() {
        assertThat(TenantStatus.ONBOARDING.canTransitionTo(TenantStatus.SUSPENDED)).isFalse();
        assertThat(TenantStatus.ONBOARDING.canTransitionTo(TenantStatus.ACTIVE)).isTrue();
    }
}
