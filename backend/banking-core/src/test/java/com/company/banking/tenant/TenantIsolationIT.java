package com.company.banking.tenant;

import com.company.banking.support.Fixtures;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Staff of one institution can neither see nor change another institution's data through any API, and existence
 * of foreign resources is never revealed (404, not 403).
 */
class TenantIsolationIT extends IntegrationTest {

    @Test
    void foreignResourcesAreInvisible() {
        TenantHandle a = fixtures.onboardTenant();
        TenantHandle b = fixtures.onboardTenant();
        UUID foreignBranch = fixtures.createBranch(b, "BRB");

        api.get("/api/v1/branches/" + foreignBranch, a.adminToken()).expectError(404, "RESOURCE_NOT_FOUND");
        api.get("/api/v1/staff/" + b.adminId(), a.adminToken()).expectError(404, "RESOURCE_NOT_FOUND");
        api.get("/api/v1/roles/" + b.roles().get("TELLER"), a.adminToken()).expectError(404, "RESOURCE_NOT_FOUND");

        List<String> visibleBranchIds = ids(api.get("/api/v1/branches?size=100", a.adminToken()).expect(200).data());
        assertThat(visibleBranchIds).containsExactly(a.headOfficeId().toString());
        List<String> visibleStaffIds = ids(api.get("/api/v1/staff?size=100", a.adminToken()).expect(200).data());
        assertThat(visibleStaffIds).containsExactly(a.adminId().toString());

        JsonNode foreignAudit = api.get("/api/v1/audit-logs?resourceId=" + foreignBranch, a.adminToken())
                .expect(200).data();
        assertThat(foreignAudit.get("totalItems").asLong()).isZero();
    }

    @Test
    void foreignResourcesCannotBeModified() {
        TenantHandle a = fixtures.onboardTenant();
        TenantHandle b = fixtures.onboardTenant();
        UUID foreignBranch = fixtures.createBranch(b, "BRB");
        StaffHandle ownStaff = fixtures.createStaff(a, "clerk", a.headOfficeId(), false, "TELLER");

        api.put("/api/v1/branches/" + foreignBranch, a.adminToken(), Map.of("name", "Hijacked", "version", 0))
                .expectError(404, "RESOURCE_NOT_FOUND");
        api.post("/api/v1/branches/" + foreignBranch + "/status", a.adminToken(),
                Map.of("status", "INACTIVE", "reason", "x", "version", 0)).expectError(404, "RESOURCE_NOT_FOUND");
        api.post("/api/v1/staff/" + b.adminId() + "/status", a.adminToken(),
                Map.of("status", "SUSPENDED", "reason", "x", "version", 0)).expectError(404, "RESOURCE_NOT_FOUND");
        api.post("/api/v1/staff/" + b.adminId() + "/credentials/reset", a.adminToken(), null)
                .expectError(404, "RESOURCE_NOT_FOUND");
        api.put("/api/v1/staff/" + b.adminId() + "/roles", a.adminToken(),
                Map.of("roleIds", List.of(a.roles().get("TELLER")))).expectError(404, "RESOURCE_NOT_FOUND");
        api.put("/api/v1/staff/" + ownStaff.id() + "/roles", a.adminToken(),
                Map.of("roleIds", List.of(b.roles().get("TELLER")))).expectError(404, "RESOURCE_NOT_FOUND");

        Map<String, Object> staffInForeignBranch = Map.of("employeeNumber", "E-X1", "firstName", "Cross",
                "lastName", "Tenant", "email", "cross@a.test", "homeBranchId", foreignBranch.toString(),
                "allBranchesAccess", false, "username", "cross");
        api.post("/api/v1/staff", a.adminToken(), staffInForeignBranch).expectError(404, "RESOURCE_NOT_FOUND");

        // b's data is untouched
        JsonNode branch = api.get("/api/v1/branches/" + foreignBranch, b.adminToken()).expect(200).data();
        assertThat(branch.get("name").asString()).isEqualTo("BRB Branch");
        assertThat(branch.get("status").asString()).isEqualTo("ACTIVE");
    }

    @Test
    void credentialsOnlyWorkForTheirOwnInstitution() {
        TenantHandle a = fixtures.onboardTenant();
        TenantHandle b = fixtures.onboardTenant();
        fixtures.createStaff(a, "onlyina", a.headOfficeId(), false, "TELLER");

        api.post("/api/v1/auth/staff/login", null, Map.of("tenantCode", b.code(), "username", "onlyina",
                "password", Fixtures.STAFF_PASSWORD)).expectError(401, "INVALID_CREDENTIALS");
        fixtures.login(a.code(), "onlyina", Fixtures.STAFF_PASSWORD);
    }

    @Test
    void tenantHintsInTheRequestAreIgnored() throws Exception {
        TenantHandle a = fixtures.onboardTenant();
        TenantHandle b = fixtures.onboardTenant();

        mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + a.adminToken())
                        .header("X-Tenant-Id", b.id().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SPOOF\",\"name\":\"Spoofed\",\"branchType\":\"BRANCH\","
                                + "\"tenantId\":\"" + b.id() + "\"}"))
                .andReturn();

        assertThat(codes(api.get("/api/v1/branches", a.adminToken()).expect(200).data())).contains("SPOOF");
        assertThat(codes(api.get("/api/v1/branches", b.adminToken()).expect(200).data())).doesNotContain("SPOOF");
    }

    private static List<String> ids(JsonNode page) {
        List<String> values = new ArrayList<>();
        page.get("items").forEach(item -> values.add(item.get("id").asString()));
        return values;
    }

    private static List<String> codes(JsonNode page) {
        List<String> values = new ArrayList<>();
        page.get("items").forEach(item -> values.add(item.get("code").asString()));
        return values;
    }
}
