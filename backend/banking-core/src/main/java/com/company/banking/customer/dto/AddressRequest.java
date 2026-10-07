package com.company.banking.customer.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param version required when updating an existing address
 */
public record AddressRequest(
        @NotBlank @Pattern(regexp = "^(RESIDENTIAL|BUSINESS|MAILING|PERMANENT)$") String addressType,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @Size(max = 100) String city,
        @Size(max = 100) String district,
        @Size(max = 100) String region,
        @NotBlank @Pattern(regexp = ValidationPatterns.COUNTRY_CODE) String countryCode,
        @Pattern(regexp = ValidationPatterns.DIGITAL_ADDRESS) String digitalAddress,
        @Size(max = 200) String landmark,
        boolean primary,
        Long version) {
}
