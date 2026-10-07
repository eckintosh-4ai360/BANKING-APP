package com.company.banking.staff.dto;

import java.util.UUID;

/**
 * List item; use GET /staff/{id} for roles and login state.
 */
public record StaffSummary(
        UUID id,
        String employeeNumber,
        String firstName,
        String lastName,
        String email,
        String jobTitle,
        UUID homeBranchId,
        boolean allBranchesAccess,
        String status) {
}
