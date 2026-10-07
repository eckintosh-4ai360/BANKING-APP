package com.company.banking.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpenKycCaseRequest(
        @NotBlank @Pattern(regexp = "^(ONBOARDING|PERIODIC_REVIEW|UPGRADE|UPDATE)$") String caseType,
        @NotBlank @Size(max = 30) String targetTierCode) {
}
