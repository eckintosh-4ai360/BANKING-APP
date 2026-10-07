package com.company.banking.tenant.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateFeatureRequest(@NotNull Boolean enabled) {
}
