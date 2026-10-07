package com.company.banking.platform;

import com.company.banking.support.Fixtures;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformOnboardingIT extends IntegrationTest {

    @Test
    void onboardingCreatesAnActiveInstitutionWithAdministratorRolesAndHeadOffice() {
        TenantHandle tenant = fixtures.onboardTenant();

        JsonNode institution = api.get("/api/v1/institution", tenant.adminToken()).expect(200).data();
        assertThat(institution.at("/institution/code").asString()).isEqualTo(tenant.code());
        assertThat(institution.at("/institution/status").asString()).isEqualTo("ACTIVE");
        assertThat(featureState(institution, "SAVINGS")).isEqualTo("licensed=true,enabled=true");
        assertThat(featureState(institution, "SUSU")).isEqualTo("licensed=false,enabled=false");

        assertThat(tenant.roles()).containsKeys("INSTITUTION_ADMIN", "BRANCH_MANAGER", "TELLER", "LOAN_OFFICER",
                "FIELD_OFFICER", "COMPLIANCE_OFFICER", "AUDITOR");

        JsonNode me = api.get("/api/v1/me", tenant.adminToken()).expect(200).data();
        assertThat(me.get("allBranchesAccess").asBoolean()).isTrue();
        assertThat(me.get("homeBranchId").asString()).isEqualTo(tenant.headOfficeId().toString());
        assertThat(textValues(me.get("permissions"))).contains("staff.create", "branch.manage")
                .doesNotContain("transaction.create", "loan.approve", "ledger.post");

        JsonNode audit = api.get("/api/v1/audit-logs?action=TENANT_ONBOARDED", tenant.adminToken())
                .expect(200).data();
        assertThat(audit.get("totalItems").asLong()).isEqualTo(1);
        assertThat(audit.at("/items/0/actorType").asString()).isEqualTo("PLATFORM_ADMIN");
    }

    @Test
    void temporaryPasswordGrantsNoPermissionsUntilChanged() {
        String code = "t" + System.nanoTime();
        JsonNode onboarded = api.post("/api/v1/platform/tenants", fixtures.platformToken(),
                Fixtures.onboardingRequest(code)).expect(201).data();
        String temporaryPassword = onboarded.at("/administratorCredential/temporaryPassword").asString();

        Fixtures.Tokens tokens = fixtures.login(code, "admin", temporaryPassword);
        assertThat(tokens.passwordChangeRequired()).isTrue();
        api.get("/api/v1/branches", tokens.accessToken()).expectError(403, "PASSWORD_CHANGE_REQUIRED");

        Fixtures.Tokens changed = fixtures.changePassword(tokens.accessToken(), temporaryPassword,
                Fixtures.STAFF_PASSWORD);
        assertThat(changed.passwordChangeRequired()).isFalse();
        api.get("/api/v1/branches", changed.accessToken()).expect(200);
        // the session that used the temporary password was ended
        api.get("/api/v1/branches", tokens.accessToken()).expectError(401, "SESSION_REVOKED");
    }

    @Test
    void duplicateInstitutionCodeIsRejected() {
        TenantHandle tenant = fixtures.onboardTenant();
        api.post("/api/v1/platform/tenants", fixtures.platformToken(), Fixtures.onboardingRequest(tenant.code()))
                .expectError(409, "TENANT_CODE_TAKEN");
    }

    @Test
    void suspendingAnInstitutionEndsSessionsAndBlocksLogin() {
        TenantHandle tenant = fixtures.onboardTenant();
        String platformToken = fixtures.platformToken();

        api.post("/api/v1/platform/tenants/" + tenant.id() + "/status", platformToken,
                Map.of("status", "SUSPENDED", "reason", "Licence review")).expect(200);

        api.get("/api/v1/institution", tenant.adminToken()).expectError(401, "SESSION_REVOKED");
        api.post("/api/v1/auth/staff/login", null, Map.of("tenantCode", tenant.code(), "username", "admin",
                "password", Fixtures.STAFF_PASSWORD)).expectError(403, "ACCOUNT_DISABLED");
        api.get("/api/v1/public/institutions/" + tenant.code() + "/branding", null).expect(404);

        api.post("/api/v1/platform/tenants/" + tenant.id() + "/status", platformToken,
                Map.of("status", "ACTIVE", "reason", "Review complete")).expect(200);
        fixtures.login(tenant.code(), "admin", Fixtures.STAFF_PASSWORD);

        JsonNode platformAudit = api.get("/api/v1/platform/audit-logs?action=TENANT_STATUS_CHANGED&resourceId="
                + tenant.id(), platformToken).expect(200).data();
        assertThat(platformAudit.get("totalItems").asLong()).isEqualTo(2);
    }

    @Test
    void featuresCanOnlyBeEnabledWithinTheLicence() {
        TenantHandle tenant = fixtures.onboardTenant();
        api.put("/api/v1/institution/features/SUSU", tenant.adminToken(), Map.of("enabled", true))
                .expectError(422, "FEATURE_NOT_LICENSED");

        api.put("/api/v1/platform/tenants/" + tenant.id() + "/features/SUSU", fixtures.platformToken(),
                Map.of("licensed", true)).expect(200);
        api.put("/api/v1/institution/features/SUSU", tenant.adminToken(), Map.of("enabled", false)).expect(200);
        JsonNode enabled = api.put("/api/v1/institution/features/SUSU", tenant.adminToken(),
                Map.of("enabled", true)).expect(200).data();
        assertThat(enabled.get("enabled").asBoolean()).isTrue();

        // revoking the licence also disables the feature
        api.put("/api/v1/platform/tenants/" + tenant.id() + "/features/SUSU", fixtures.platformToken(),
                Map.of("licensed", false)).expect(200);
        JsonNode institution = api.get("/api/v1/institution", tenant.adminToken()).expect(200).data();
        assertThat(featureState(institution, "SUSU")).isEqualTo("licensed=false,enabled=false");
    }

    @Test
    void platformAndStaffTokensAreConfinedToTheirOwnApi() {
        TenantHandle tenant = fixtures.onboardTenant();
        String platformToken = fixtures.platformToken();

        api.get("/api/v1/branches", platformToken).expectError(403, "ACCESS_DENIED");
        api.get("/api/v1/platform/tenants", tenant.adminToken()).expectError(403, "ACCESS_DENIED");
        api.get("/api/v1/platform/tenants", null).expectError(401, "UNAUTHENTICATED");
        api.get("/api/v1/branches", "not-a-jwt").expectError(401, "UNAUTHENTICATED");
    }

    @Test
    void publicBrandingIsAvailableBeforeLogin() {
        TenantHandle tenant = fixtures.onboardTenant();
        JsonNode branding = api.get("/api/v1/public/institutions/" + tenant.code() + "/branding", null)
                .expect(200).data();
        assertThat(branding.get("primaryColor").asString()).isEqualTo("#0B3B60");
        assertThat(textValues(branding.get("enabledFeatures"))).containsExactly("LOANS", "SAVINGS");
        api.get("/api/v1/public/institutions/does-not-exist/branding", null).expectError(404, "RESOURCE_NOT_FOUND");
    }

    private static String featureState(JsonNode institution, String code) {
        for (JsonNode feature : institution.get("features")) {
            if (code.equals(feature.get("code").asString())) {
                return "licensed=" + feature.get("licensed").asBoolean() + ",enabled="
                        + feature.get("enabled").asBoolean();
            }
        }
        throw new AssertionError("Feature not listed: " + code);
    }

    private static List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asString()));
        return values;
    }
}
