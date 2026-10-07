package com.company.banking.tenant.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record UpdateBrandingRequest(
        @URL(protocol = "https") @Size(max = 500) String logoUrl,
        @NotBlank @Pattern(regexp = ValidationPatterns.HEX_COLOR) String primaryColor,
        @NotBlank @Pattern(regexp = ValidationPatterns.HEX_COLOR) String secondaryColor,
        @Pattern(regexp = ValidationPatterns.SMS_SENDER_ID) String smsSenderId,
        @Size(max = 100) String emailSenderName,
        @Email @Size(max = 254) String emailSenderAddress,
        @NotNull Long version) {
}
