package com.company.banking.iam;

import com.company.banking.support.Fixtures;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.Fixtures.Tokens;
import com.company.banking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StaffAuthenticationIT extends IntegrationTest {

    private static final String LOGIN = "/api/v1/auth/staff/login";
    private static final String REFRESH = "/api/v1/auth/token/refresh";

    @Test
    void wrongPasswordUnknownUserAndUnknownInstitutionLookIdentical() {
        TenantHandle tenant = fixtures.onboardTenant();

        api.post(LOGIN, null, login(tenant.code(), "admin", "Wrong-Password-123"))
                .expectError(401, "INVALID_CREDENTIALS");
        api.post(LOGIN, null, login(tenant.code(), "nobody", Fixtures.STAFF_PASSWORD))
                .expectError(401, "INVALID_CREDENTIALS");
        api.post(LOGIN, null, login("no-such-institution", "admin", Fixtures.STAFF_PASSWORD))
                .expectError(401, "INVALID_CREDENTIALS");

        JsonNode failures = api.get("/api/v1/audit-logs?action=LOGIN_FAILED", tenant.adminToken()).expect(200).data();
        assertThat(failures.get("totalItems").asLong()).isEqualTo(2);
        assertThat(failures.toString()).doesNotContain("Wrong-Password-123");
    }

    @Test
    void accountLocksAfterRepeatedFailuresUntilAnAdministratorResetsIt() {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle teller = fixtures.createStaff(tenant, "teller.lock", tenant.headOfficeId(), false, "TELLER");

        for (int attempt = 1; attempt <= 4; attempt++) {
            api.post(LOGIN, null, login(tenant.code(), teller.username(), "Wrong-Password-" + attempt))
                    .expectError(401, "INVALID_CREDENTIALS");
        }
        api.post(LOGIN, null, login(tenant.code(), teller.username(), "Wrong-Password-5"))
                .expectError(423, "ACCOUNT_LOCKED");
        api.post(LOGIN, null, login(tenant.code(), teller.username(), Fixtures.STAFF_PASSWORD))
                .expectError(423, "ACCOUNT_LOCKED");

        JsonNode staff = api.get("/api/v1/staff/" + teller.id(), tenant.adminToken()).expect(200).data();
        assertThat(staff.at("/login/locked").asBoolean()).isTrue();

        String temporary = api.post("/api/v1/staff/" + teller.id() + "/credentials/reset", tenant.adminToken(), null)
                .expect(200).data().get("temporaryPassword").asString();
        Tokens tokens = fixtures.login(tenant.code(), teller.username(), temporary);
        assertThat(tokens.passwordChangeRequired()).isTrue();
    }

    @Test
    void refreshRotatesTokensAndReuseOfAnOldTokenEndsTheSession() {
        TenantHandle tenant = fixtures.onboardTenant();
        Tokens first = fixtures.login(tenant.code(), "admin", Fixtures.STAFF_PASSWORD);

        JsonNode second = api.post(REFRESH, null, Map.of("refreshToken", first.refreshToken())).expect(200).data();
        String secondRefresh = second.get("refreshToken").asString();
        String secondAccess = second.get("accessToken").asString();
        assertThat(secondRefresh).isNotEqualTo(first.refreshToken());
        api.get("/api/v1/me", secondAccess).expect(200);

        // replaying the rotated token is treated as theft: the whole session ends
        api.post(REFRESH, null, Map.of("refreshToken", first.refreshToken()))
                .expectError(401, "INVALID_REFRESH_TOKEN");
        api.post(REFRESH, null, Map.of("refreshToken", secondRefresh)).expectError(401, "INVALID_REFRESH_TOKEN");
        api.get("/api/v1/me", secondAccess).expectError(401, "SESSION_REVOKED");

        JsonNode reuse = api.get("/api/v1/audit-logs?action=REFRESH_TOKEN_REUSE_DETECTED", tenant.adminToken())
                .expect(200).data();
        assertThat(reuse.get("totalItems").asLong()).isEqualTo(1);
    }

    @Test
    void logoutEndsTheSessionImmediately() {
        TenantHandle tenant = fixtures.onboardTenant();
        Tokens tokens = fixtures.login(tenant.code(), "admin", Fixtures.STAFF_PASSWORD);

        api.post("/api/v1/auth/logout", tokens.accessToken(), null).expect(200);
        api.get("/api/v1/me", tokens.accessToken()).expectError(401, "SESSION_REVOKED");
        api.post(REFRESH, null, Map.of("refreshToken", tokens.refreshToken()))
                .expectError(401, "INVALID_REFRESH_TOKEN");
    }

    @Test
    void malformedOrForgedRefreshTokensAreRejected() {
        TenantHandle tenant = fixtures.onboardTenant();
        Tokens tokens = fixtures.login(tenant.code(), "admin", Fixtures.STAFF_PASSWORD);
        String forged = tokens.refreshToken().substring(0, tokens.refreshToken().length() - 4) + "AAAA";

        api.post(REFRESH, null, Map.of("refreshToken", "garbage")).expectError(401, "INVALID_REFRESH_TOKEN");
        api.post(REFRESH, null, Map.of("refreshToken", "s.not-a-uuid.abc")).expectError(401, "INVALID_REFRESH_TOKEN");
        api.post(REFRESH, null, Map.of("refreshToken", forged)).expectError(401, "INVALID_REFRESH_TOKEN");
    }

    @Test
    void passwordPolicyIsEnforced() {
        TenantHandle tenant = fixtures.onboardTenant();
        Tokens tokens = fixtures.login(tenant.code(), "admin", Fixtures.STAFF_PASSWORD);

        api.post("/api/v1/auth/password", tokens.accessToken(), Map.of("currentPassword", Fixtures.STAFF_PASSWORD,
                "newPassword", "short")).expectError(400, "VALIDATION_FAILED");
        api.post("/api/v1/auth/password", tokens.accessToken(), Map.of("currentPassword", Fixtures.STAFF_PASSWORD,
                "newPassword", "aaaaaaaaaaaaaaaa")).expectError(422, "PASSWORD_POLICY_VIOLATION");
        api.post("/api/v1/auth/password", tokens.accessToken(), Map.of("currentPassword", Fixtures.STAFF_PASSWORD,
                "newPassword", "my-admin-is-great-1")).expectError(422, "PASSWORD_POLICY_VIOLATION");
        api.post("/api/v1/auth/password", tokens.accessToken(), Map.of("currentPassword", Fixtures.STAFF_PASSWORD,
                "newPassword", Fixtures.STAFF_PASSWORD)).expectError(422, "PASSWORD_POLICY_VIOLATION");
        api.post("/api/v1/auth/password", tokens.accessToken(), Map.of("currentPassword", "Not-The-Password-1",
                "newPassword", "Another-Long-Phrase-77")).expectError(401, "INVALID_CREDENTIALS");
    }

    @Test
    void suspendedStaffLoseTheirSessionsAndCannotSignIn() {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle teller = fixtures.createStaff(tenant, "teller.susp", tenant.headOfficeId(), false, "TELLER");
        long version = api.get("/api/v1/staff/" + teller.id(), tenant.adminToken()).expect(200).data()
                .get("version").asLong();

        api.post("/api/v1/staff/" + teller.id() + "/status", tenant.adminToken(),
                Map.of("status", "SUSPENDED", "reason", "Investigation", "version", version)).expect(200);

        api.get("/api/v1/me", teller.token()).expectError(401, "SESSION_REVOKED");
        api.post(LOGIN, null, login(tenant.code(), teller.username(), Fixtures.STAFF_PASSWORD))
                .expectError(403, "ACCOUNT_DISABLED");
        api.post(REFRESH, null, Map.of("refreshToken", teller.tokens().refreshToken()))
                .expectError(401, "INVALID_REFRESH_TOKEN");

        api.post("/api/v1/staff/" + teller.id() + "/status", tenant.adminToken(),
                Map.of("status", "ACTIVE", "reason", "Cleared", "version", version + 1)).expect(200);
        fixtures.login(tenant.code(), teller.username(), Fixtures.STAFF_PASSWORD);
    }

    @Test
    void staleAuthorizationHeaderDoesNotBlockLogin() {
        TenantHandle tenant = fixtures.onboardTenant();
        api.post(LOGIN, "expired-or-garbage-token", login(tenant.code(), "admin", Fixtures.STAFF_PASSWORD))
                .expect(200);
    }

    @Test
    void responsesCarryACorrelationIdAndNoInternalDetails() {
        api.get("/api/v1/branches", "garbage").expectError(401, "UNAUTHENTICATED");
        JsonNode error = api.post(LOGIN, null, "{not json").expectError(400, "MALFORMED_REQUEST").body();
        assertThat(error.get("traceId").asString()).isNotBlank();
        assertThat(error.toString()).doesNotContain("Exception").doesNotContain("at com.");
    }

    private static Map<String, String> login(String tenantCode, String username, String password) {
        return Map.of("tenantCode", tenantCode, "username", username, "password", password);
    }
}
