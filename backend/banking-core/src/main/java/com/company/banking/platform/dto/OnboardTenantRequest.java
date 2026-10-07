package com.company.banking.platform.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record OnboardTenantRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.TENANT_CODE,
                message = "must be 2-32 lower-case letters, digits or hyphens") String code,
        @NotBlank @Size(max = 200) String legalName,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Pattern(regexp = "^(MICROFINANCE|SAVINGS_AND_LOANS|CREDIT_UNION|RURAL_COMMUNITY_BANK|"
                + "SUSU_OPERATOR|DIGITAL_LENDER|COOPERATIVE|OTHER)$") String institutionType,
        @NotBlank @Pattern(regexp = ValidationPatterns.COUNTRY_CODE) String countryCode,
        @NotBlank @Pattern(regexp = ValidationPatterns.CURRENCY_CODE) String baseCurrency,
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Size(max = 20) String locale,
        @Size(max = 64) String licenceNumber,
        @NotBlank @Email @Size(max = 254) String contactEmail,
        @Pattern(regexp = ValidationPatterns.PHONE) String contactPhone,
        @NotNull @Valid HeadOffice headOffice,
        @NotNull @Valid Administrator administrator,
        @NotNull @Size(max = 50) Set<@NotBlank String> features) {

    public record HeadOffice(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]{0,19}$") String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 100) String city,
            @Size(max = 100) String region,
            @Pattern(regexp = ValidationPatterns.DIGITAL_ADDRESS) String digitalAddress) {
    }

    public record Administrator(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = ValidationPatterns.PHONE) String phone,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._@-]{2,99}$") String username) {
    }
}
