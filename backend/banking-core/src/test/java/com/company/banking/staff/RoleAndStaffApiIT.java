package com.company.banking.staff;

import com.company.banking.support.Fixtures;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAndStaffApiIT extends IntegrationTest {

    @Test
    void customRoleTakesEffectImmediatelyWhenAssigned() {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle clerk = fixtures.createStaff(tenant, "clerk", tenant.headOfficeId(), false, "TELLER");

        JsonNode role = api.post("/api/v1/roles", tenant.adminToken(), Map.of("code", "cash_supervisor",
                "name", "Cash Supervisor", "permissions", List.of("cash.view", "cash.manage"))).expect(201).data();
        assertThat(role.get("code").asString()).isEqualTo("CASH_SUPERVISOR");

        api.put("/api/v1/staff/" + clerk.id() + "/roles", tenant.adminToken(),
                Map.of("roleIds", List.of(role.get("id").asString()))).expect(200);

        // the clerk's old token stops working at once; a new login carries the new permissions
        api.get("/api/v1/me", clerk.token()).expectError(401, "SESSION_REVOKED");
        String token = fixtures.login(tenant.code(), "clerk", Fixtures.STAFF_PASSWORD).accessToken();
        JsonNode me = api.get("/api/v1/me", token).expect(200).data();
        assertThat(strings(me.get("permissions"))).containsExactly("cash.manage", "cash.view");

        JsonNode audit = api.get("/api/v1/audit-logs?action=STAFF_ROLES_CHANGED&resourceId=" + clerk.id(),
                tenant.adminToken()).expect(200).data();
        assertThat(strings(audit.at("/items/0/before"))).containsExactly("TELLER");
        assertThat(strings(audit.at("/items/0/after"))).containsExactly("CASH_SUPERVISOR");
    }

    @Test
    void platformAndUnknownPermissionsCannotBeGranted() {
        TenantHandle tenant = fixtures.onboardTenant();
        api.post("/api/v1/roles", tenant.adminToken(), Map.of("code", "SNEAKY", "name", "Sneaky",
                "permissions", List.of("platform.tenant.manage"))).expectError(422, "INVALID_PERMISSION");
        api.post("/api/v1/roles", tenant.adminToken(), Map.of("code", "TYPO", "name", "Typo",
                "permissions", List.of("loan.aprove"))).expectError(422, "INVALID_PERMISSION");
    }

    @Test
    void nobodyCanChangeTheirOwnAccess() {
        TenantHandle tenant = fixtures.onboardTenant();
        String self = "/api/v1/staff/" + tenant.adminId();

        api.put(self + "/roles", tenant.adminToken(), Map.of("roleIds", List.of(tenant.roles().get("AUDITOR"))))
                .expectError(403, "SELF_MODIFICATION_NOT_ALLOWED");
        api.post(self + "/status", tenant.adminToken(), Map.of("status", "SUSPENDED", "reason", "x", "version", 0))
                .expectError(403, "SELF_MODIFICATION_NOT_ALLOWED");
        api.post(self + "/credentials/reset", tenant.adminToken(), null)
                .expectError(403, "SELF_MODIFICATION_NOT_ALLOWED");

        UUID adminRole = tenant.roles().get("INSTITUTION_ADMIN");
        JsonNode role = api.get("/api/v1/roles/" + adminRole, tenant.adminToken()).expect(200).data();
        List<String> escalated = new ArrayList<>(strings(role.get("permissions")));
        escalated.add("loan.approve");
        api.put("/api/v1/roles/" + adminRole, tenant.adminToken(), Map.of("name", "Institution Administrator",
                        "status", "ACTIVE", "permissions", escalated, "version", role.get("version").asLong()))
                .expectError(403, "SELF_MODIFICATION_NOT_ALLOWED");
    }

    @Test
    void assigningRolesAtCreationRequiresRoleAssignPermission() {
        TenantHandle tenant = fixtures.onboardTenant();
        JsonNode hrRole = api.post("/api/v1/roles", tenant.adminToken(), Map.of("code", "HR_CLERK",
                "name", "HR Clerk", "permissions", List.of("staff.view", "staff.create"))).expect(201).data();
        StaffHandle hr = fixtures.createStaff(tenant, "hr.clerk", tenant.headOfficeId(), false, "TELLER");
        api.put("/api/v1/staff/" + hr.id() + "/roles", tenant.adminToken(),
                Map.of("roleIds", List.of(hrRole.get("id").asString()))).expect(200);
        String hrToken = fixtures.login(tenant.code(), "hr.clerk", Fixtures.STAFF_PASSWORD).accessToken();

        Map<String, Object> withRoles = newStaff(tenant, "new.one", tenant.headOfficeId());
        withRoles.put("roleIds", List.of(tenant.roles().get("TELLER").toString()));
        api.post("/api/v1/staff", hrToken, withRoles).expectError(403, "ACCESS_DENIED");

        JsonNode created = api.post("/api/v1/staff", hrToken, newStaff(tenant, "new.two", tenant.headOfficeId()))
                .expect(201).data();
        assertThat(created.at("/staff/roles").size()).isZero();
        assertThat(created.at("/credential/temporaryPassword").asString()).hasSize(16);
    }

    @Test
    void branchScopedAdministratorsCannotGrantWiderAccess() {
        TenantHandle tenant = fixtures.onboardTenant();
        UUID tema = fixtures.createBranch(tenant, "TMA");
        UUID kumasi = fixtures.createBranch(tenant, "KSI");
        StaffHandle localAdmin = fixtures.createStaff(tenant, "admin.tema", tema, false, "INSTITUTION_ADMIN");

        Map<String, Object> allBranches = newStaff(tenant, "wide", tema);
        allBranches.put("allBranchesAccess", true);
        api.post("/api/v1/staff", localAdmin.token(), allBranches).expectError(403, "ACCESS_DENIED");
        api.post("/api/v1/staff", localAdmin.token(), newStaff(tenant, "elsewhere", kumasi))
                .expectError(404, "RESOURCE_NOT_FOUND");
        api.post("/api/v1/staff", localAdmin.token(), newStaff(tenant, "local", tema)).expect(201);

        JsonNode visible = api.get("/api/v1/staff?size=100", localAdmin.token()).expect(200).data();
        visible.get("items").forEach(staff ->
                assertThat(staff.get("homeBranchId").asString()).isEqualTo(tema.toString()));
    }

    @Test
    void staffLifecycleRules() {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle teller = fixtures.createStaff(tenant, "teller.life", tenant.headOfficeId(), false, "TELLER");

        api.post("/api/v1/staff", tenant.adminToken(), newStaff(tenant, "teller.life", tenant.headOfficeId()))
                .expectError(409, "DUPLICATE_RESOURCE");

        api.post("/api/v1/staff/" + teller.id() + "/status", tenant.adminToken(),
                Map.of("status", "TERMINATED", "reason", "Resigned", "version", 0)).expect(200);
        api.post("/api/v1/staff/" + teller.id() + "/status", tenant.adminToken(),
                        Map.of("status", "ACTIVE", "reason", "Rehire", "version", 1))
                .expectError(422, "INVALID_STATE_TRANSITION");
        api.post("/api/v1/staff/" + teller.id() + "/credentials/reset", tenant.adminToken(), null)
                .expectError(422, "INVALID_STATE_TRANSITION");

        api.get("/api/v1/staff", teller.token()).expectError(401, "SESSION_REVOKED");
    }

    @Test
    void tellersCannotAdministerStaffOrRoles() {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle teller = fixtures.createStaff(tenant, "teller.min", tenant.headOfficeId(), false, "TELLER");

        api.get("/api/v1/staff", teller.token()).expectError(403, "ACCESS_DENIED");
        api.get("/api/v1/roles", teller.token()).expectError(403, "ACCESS_DENIED");
        api.post("/api/v1/staff", teller.token(), newStaff(tenant, "x.y", tenant.headOfficeId()))
                .expectError(403, "ACCESS_DENIED");
        api.get("/api/v1/me", teller.token()).expect(200);
    }

    private static Map<String, Object> newStaff(TenantHandle tenant, String username, UUID branchId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("employeeNumber", "E-" + username.toUpperCase());
        request.put("firstName", "New");
        request.put("lastName", "Person");
        request.put("email", username + "@" + tenant.code() + ".test");
        request.put("homeBranchId", branchId.toString());
        request.put("allBranchesAccess", false);
        request.put("username", username);
        return request;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asString()));
        return values;
    }
}
