package com.company.banking.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateRoleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        @NotBlank @Pattern(regexp = "^(ACTIVE|INACTIVE)$") String status,
        @NotNull @Size(max = 200) Set<@NotBlank String> permissions,
        @NotNull Long version) {
}
