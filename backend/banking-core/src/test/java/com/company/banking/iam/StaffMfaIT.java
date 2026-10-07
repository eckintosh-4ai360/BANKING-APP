package com.company.banking.iam;

import com.company.banking.iam.security.mfa.Base32;
import com.company.banking.iam.security.mfa.Totp;
import com.company.banking.support.Fixtures;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StaffMfaIT extends IntegrationTest {

    private static final String LOGIN = "/api/v1/auth/staff/login";
    private static final String VERIFY = "/api/v1/auth/mfa/verify";

    @Test
    void enrolledStaffNeedAnAuthenticatorCodeToSignIn() {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle teller = fixtures.createStaff(tenant, "teller.mfa", tenant.headOfficeId(), false, "TELLER");

        JsonNode setup = api.post("/api/v1/auth/mfa/totp/setup", teller.token(), null).expect(200).data();
        byte[] secret = Base32.decode(setup.get("secret").asString());
        assertThat(setup.get("otpauthUri").asString())
                .startsWith("otpauth://totp/Banking%20Core:teller.mfa%40" + tenant.code())
                .contains("digits=6", "period=30");

        api.post("/api/v1/auth/mfa/totp/activate", teller.token(), Map.of("code", "000000"))
                .expectError(401, "INVALID_MFA_CODE");
        long step = Totp.stepAt(Instant.now());
        JsonNode activated = api.post("/api/v1/auth/mfa/totp/activate", teller.token(),
                Map.of("code", Totp.code(secret, step, 6))).expect(200).data();
        api.get("/api/v1/me", teller.token()).expectError(401, "SESSION_REVOKED");
        api.get("/api/v1/me", activated.get("accessToken").asString()).expect(200);
        api.post("/api/v1/auth/mfa/totp/setup", activated.get("accessToken").asString(), null)
                .expectError(409, "MFA_ALREADY_ENABLED");

        JsonNode challenge = api.post(LOGIN, null, login(tenant.code(), "teller.mfa")).expect(200).data();
        assertThat(challenge.get("mfaRequired").asBoolean()).isTrue();
        assertThat(challenge.has("accessToken")).isFalse();
        String challengeToken = challenge.get("mfaChallengeToken").asString();

        // The challenge is not an access token.
        api.get("/api/v1/me", challengeToken).expectError(401, "UNAUTHENTICATED");
        api.post(VERIFY, null, Map.of("challengeToken", challengeToken, "code", "123456"))
                .expectError(401, "INVALID_MFA_CODE");
        api.post(VERIFY, null, Map.of("challengeToken", "forged", "code", "123456"))
                .expectError(401, "INVALID_MFA_CHALLENGE");

        String nextCode = Totp.code(secret, step + 1, 6);
        JsonNode signedIn = api.post(VERIFY, null, Map.of("challengeToken", challengeToken, "code", nextCode))
                .expect(200).data();
        api.get("/api/v1/me", signedIn.get("accessToken").asString()).expect(200);

        // A code works once.
        api.post(VERIFY, null, Map.of("challengeToken", challengeToken, "code", nextCode))
                .expectError(401, "INVALID_MFA_CODE");
    }

    @Test
    void anAdministratorResetRemovesALostAuthenticator() {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle teller = fixtures.createStaff(tenant, "teller.lost", tenant.headOfficeId(), false, "TELLER");
        JsonNode setup = api.post("/api/v1/auth/mfa/totp/setup", teller.token(), null).expect(200).data();
        api.post("/api/v1/auth/mfa/totp/activate", teller.token(), Map.of("code",
                Totp.code(Base32.decode(setup.get("secret").asString()), Totp.stepAt(Instant.now()), 6)))
                .expect(200);

        String temporary = api.post("/api/v1/staff/" + teller.id() + "/credentials/reset", tenant.adminToken(), null)
                .expect(200).data().get("temporaryPassword").asString();
        JsonNode tokens = api.post(LOGIN, null, Map.of("tenantCode", tenant.code(), "username", "teller.lost",
                "password", temporary)).expect(200).data();
        assertThat(tokens.get("mfaRequired").asBoolean()).isFalse();
        assertThat(tokens.get("passwordChangeRequired").asBoolean()).isTrue();
    }

    private static Map<String, String> login(String tenantCode, String username) {
        return Map.of("tenantCode", tenantCode, "username", username, "password", Fixtures.STAFF_PASSWORD);
    }
}
