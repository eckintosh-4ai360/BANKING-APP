package com.company.banking.staff.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateStaffRequest(
        @NotBlank @Size(max = 30) String employeeNumber,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @Pattern(regexp = ValidationPatterns.PHONE) String phone,
        @Size(max = 100) String jobTitle,
        @NotNull UUID homeBranchId,
        boolean allBranchesAccess,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._@-]{2,99}$",
                message = "must be 3-100 letters, digits or . _ @ -") String username,
        @Size(max = 20) Set<@NotNull UUID> roleIds) {

    public Set<UUID> roleIdsOrEmpty() {
        return roleIds == null ? Set.of() : roleIds;
    }
}
