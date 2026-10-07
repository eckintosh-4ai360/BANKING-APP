package com.company.banking.iam.dto;

/**
 * Shown once while enrolling: the secret (for manual entry) and the otpauth URI (for a QR code).
 */
public record MfaSetupResponse(String secret, String otpauthUri) {

    @Override
    public String toString() {
        return "MfaSetupResponse[***]";
    }
}
