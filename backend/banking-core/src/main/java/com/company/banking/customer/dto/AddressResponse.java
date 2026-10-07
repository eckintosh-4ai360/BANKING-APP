package com.company.banking.customer.dto;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        String addressType,
        String line1,
        String line2,
        String city,
        String district,
        String region,
        String countryCode,
        String digitalAddress,
        String landmark,
        boolean primary,
        boolean active,
        Instant verifiedAt,
        Long version) {
}
