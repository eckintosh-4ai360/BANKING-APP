package com.company.banking.iam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Result of a credential exchange. Either tokens, or (when the account uses MFA) a short-lived challenge to present
 * with an authenticator code to {@code POST /api/v1/auth/mfa/verify}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        boolean passwordChangeRequired,
        boolean mfaEnrollmentRequired,
        boolean mfaRequired,
        String mfaChallengeToken,
        Instant mfaChallengeExpiresAt) {

    public static TokenResponse bearer(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
                                       Instant refreshTokenExpiresAt, boolean passwordChangeRequired,
                                       boolean mfaEnrollmentRequired) {
        return new TokenResponse("Bearer", accessToken, accessTokenExpiresAt, refreshToken, refreshTokenExpiresAt,
                passwordChangeRequired, mfaEnrollmentRequired, false, null, null);
    }

    public static TokenResponse mfaChallenge(String challengeToken, Instant expiresAt) {
        return new TokenResponse(null, null, null, null, null, false, false, true, challengeToken, expiresAt);
    }

    @Override
    public String toString() {
        return "TokenResponse[***]";
    }
}
