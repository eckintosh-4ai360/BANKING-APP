package com.company.banking.customer.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param version required when updating an existing record
 */
public record NextOfKinRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Size(max = 50) String relationship,
        @Pattern(regexp = ValidationPatterns.PHONE) String phone,
        @Email @Size(max = 254) String email,
        @Size(max = 300) String address,
        boolean primary,
        Long version) {
}
