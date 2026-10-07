package com.company.banking.iam.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean systemRole,
        String status,
        List<String> permissions,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
