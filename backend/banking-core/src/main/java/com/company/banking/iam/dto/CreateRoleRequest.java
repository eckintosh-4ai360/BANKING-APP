package com.company.banking.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateRoleRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,49}$",
                message = "must be 2-50 letters, digits or underscores") String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        @NotNull @Size(max = 200) Set<@NotBlank String> permissions) {
}
