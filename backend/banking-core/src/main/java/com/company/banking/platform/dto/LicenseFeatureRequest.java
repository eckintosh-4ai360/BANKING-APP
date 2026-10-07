package com.company.banking.platform.dto;

import jakarta.validation.constraints.NotNull;

public record LicenseFeatureRequest(@NotNull Boolean licensed) {
}
