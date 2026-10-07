package com.company.banking.branch.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateBranchRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]{0,19}$",
                message = "must be 1-20 letters, digits or hyphens") String code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "^(HEAD_OFFICE|BRANCH|AGENCY)$") String branchType,
        @Pattern(regexp = ValidationPatterns.PHONE) String phone,
        @Email @Size(max = 254) String email,
        @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String region,
        @Pattern(regexp = ValidationPatterns.DIGITAL_ADDRESS) String digitalAddress,
        LocalDate openedOn) {
}
