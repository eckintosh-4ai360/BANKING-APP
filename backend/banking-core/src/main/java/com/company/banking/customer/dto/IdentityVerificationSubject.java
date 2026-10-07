package com.company.banking.customer.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Decrypted identity data handed to an identity-verification provider. Lives only in memory; never logged.
 */
public record IdentityVerificationSubject(
        UUID identificationId,
        String idTypeCode,
        String idNumber,
        String issuingCountry,
        String firstName,
        String middleName,
        String lastName,
        LocalDate dateOfBirth,
        String registeredName) {

    @Override
    public String toString() {
        return "IdentityVerificationSubject[identificationId=" + identificationId + ", ***]";
    }
}
