package com.company.banking.platform.dto;

import com.company.banking.branch.dto.BranchResponse;
import com.company.banking.iam.dto.IssuedCredential;
import com.company.banking.tenant.dto.TenantSummary;

import java.util.UUID;

/**
 * Result of onboarding. The administrator's temporary password is shown exactly once; deliver it out of band.
 */
public record OnboardTenantResponse(
        TenantSummary tenant,
        BranchResponse headOffice,
        UUID administratorId,
        IssuedCredential administratorCredential) {
}
