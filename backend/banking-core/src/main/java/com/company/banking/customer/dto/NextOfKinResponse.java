package com.company.banking.customer.dto;

import java.util.UUID;

public record NextOfKinResponse(
        UUID id,
        String fullName,
        String relationship,
        String phone,
        String email,
        String address,
        boolean primary,
        boolean active,
        Long version) {
}
