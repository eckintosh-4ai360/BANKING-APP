package com.company.banking.customer.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record IndividualDetails(
        @Size(max = 20) String title,
        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) String middleName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull @Past LocalDate dateOfBirth,
        @Pattern(regexp = "^(MALE|FEMALE|OTHER|UNDISCLOSED)$") String gender,
        @Pattern(regexp = ValidationPatterns.COUNTRY_CODE) String nationality,
        @Pattern(regexp = "^(SINGLE|MARRIED|DIVORCED|WIDOWED|SEPARATED|UNDISCLOSED)$") String maritalStatus,
        @Size(max = 100) String occupation,
        @Size(max = 150) String employerName,
        @Pattern(regexp = "^(EMPLOYED|SELF_EMPLOYED|UNEMPLOYED|STUDENT|RETIRED|OTHER)$") String employmentStatus,
        @Size(max = 30) String monthlyIncomeBand,
        @Size(min = 5, max = 30) String taxId) {

    @Override
    public String toString() {
        return "IndividualDetails[***]";
    }
}
