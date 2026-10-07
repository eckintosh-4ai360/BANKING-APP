package com.company.banking.branch.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BranchResponse(
        UUID id,
        String code,
        String name,
        String branchType,
        String status,
        String phone,
        String email,
        String addressLine1,
        String addressLine2,
        String city,
        String region,
        String digitalAddress,
        LocalDate openedOn,
        LocalDate closedOn,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
