package com.company.banking.customer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RelatedPartyResponse(
        UUID id,
        UUID relatedCustomerId,
        String fullName,
        String partyRole,
        BigDecimal ownershipPercent,
        String nationality,
        LocalDate dateOfBirth,
        String phone,
        String email,
        String idTypeCode,
        String idNumberMasked,
        boolean politicallyExposed,
        boolean active,
        Long version) {
}
