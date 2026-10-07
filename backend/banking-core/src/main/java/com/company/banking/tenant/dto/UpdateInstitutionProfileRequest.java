package com.company.banking.tenant.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record UpdateInstitutionProfileRequest(
        @NotBlank @Size(max = 120) String displayName,
        @Email @Size(max = 254) String contactEmail,
        @Pattern(regexp = ValidationPatterns.PHONE) String contactPhone,
        @Email @Size(max = 254) String supportEmail,
        @Pattern(regexp = ValidationPatterns.PHONE) String supportPhone,
        @URL(protocol = "https") @Size(max = 255) String websiteUrl,
        @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String region,
        @Pattern(regexp = ValidationPatterns.DIGITAL_ADDRESS) String digitalAddress,
        @NotNull Long version) {
}
