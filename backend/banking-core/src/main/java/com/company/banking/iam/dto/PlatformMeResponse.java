package com.company.banking.iam.dto;

import java.util.List;
import java.util.UUID;

public record PlatformMeResponse(
        UUID id,
        String username,
        String fullName,
        String email,
        String role,
        List<String> permissions,
        boolean passwordChangeRequired) {
}
