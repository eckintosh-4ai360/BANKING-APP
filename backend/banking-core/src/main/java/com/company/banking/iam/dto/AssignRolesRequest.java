package com.company.banking.iam.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * The complete set of roles the staff member should hold afterwards.
 */
public record AssignRolesRequest(@NotNull @Size(max = 20) Set<@NotNull UUID> roleIds) {
}
