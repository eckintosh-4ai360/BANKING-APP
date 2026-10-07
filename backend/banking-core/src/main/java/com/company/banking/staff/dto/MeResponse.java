package com.company.banking.staff.dto;

import com.company.banking.iam.dto.RoleSummary;

import java.util.List;
import java.util.UUID;

/**
 * The signed-in staff member, for UI personalisation and permission-aware navigation. The backend still enforces
 * every permission; hiding UI elements is cosmetic.
 */
public record MeResponse(
        UUID id,
        UUID tenantId,
        String tenantCode,
        String institutionName,
        String username,
        String firstName,
        String lastName,
        String email,
        UUID homeBranchId,
        boolean allBranchesAccess,
        List<RoleSummary> roles,
        List<String> permissions,
        boolean passwordChangeRequired) {
}
