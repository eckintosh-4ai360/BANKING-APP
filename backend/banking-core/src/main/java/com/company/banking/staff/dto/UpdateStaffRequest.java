package com.company.banking.staff.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateStaffRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @Pattern(regexp = ValidationPatterns.PHONE) String phone,
        @Size(max = 100) String jobTitle,
        @NotNull UUID homeBranchId,
        boolean allBranchesAccess,
        @NotNull Long version) {
}
