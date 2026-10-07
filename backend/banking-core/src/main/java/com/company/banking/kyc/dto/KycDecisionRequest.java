package com.company.banking.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Approval decision. A case with a watchlist or PEP hit can only be approved as HIGH risk.
 */
public record KycDecisionRequest(
        @NotBlank @Pattern(regexp = "^(LOW|MEDIUM|HIGH)$") String riskLevel,
        @Size(max = 500) String note,
        @NotNull Long version) {
}
