package com.company.banking.customer.dto;

import java.time.LocalDate;

public record IndividualProfileResponse(
        String title,
        String firstName,
        String middleName,
        String lastName,
        LocalDate dateOfBirth,
        String gender,
        String nationality,
        String maritalStatus,
        String occupation,
        String employerName,
        String employmentStatus,
        String monthlyIncomeBand,
        String taxIdMasked) {
}
