package com.company.banking.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A check performed by a person, e.g. comparing the customer to the ID photo or screening a watchlist manually.
 */
public record ManualCheckRequest(
        @NotBlank
        @Pattern(regexp = "^(IDENTITY_VERIFICATION|WATCHLIST|PEP|FACE_MATCH|ADDRESS|PHONE|DOCUMENT)$")
        String checkType,
        @NotBlank @Pattern(regexp = "^(PASS|FAIL|INCONCLUSIVE)$") String result,
        @NotBlank @Size(max = 500) String note) {
}
