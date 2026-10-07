package com.company.banking.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MfaVerifyRequest(
        @NotBlank @Size(max = 4096) String challengeToken,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "must be a 6-digit code") String code) {

    @Override
    public String toString() {
        return "MfaVerifyRequest[***]";
    }
}
