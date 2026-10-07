package com.company.banking.staff.dto;

import com.company.banking.iam.dto.CredentialInfo;
import com.company.banking.iam.dto.RoleSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffResponse(
        UUID id,
        String employeeNumber,
        String firstName,
        String lastName,
        String email,
        String phone,
        String jobTitle,
        UUID homeBranchId,
        boolean allBranchesAccess,
        String status,
        String statusReason,
        CredentialInfo login,
        List<RoleSummary> roles,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
