package com.company.banking.branch;

import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BranchApiIT extends IntegrationTest {

    @Test
    void branchLifecycleIsVersionedAndAudited() {
        TenantHandle tenant = fixtures.onboardTenant();

        JsonNode created = api.post("/api/v1/branches", tenant.adminToken(), Map.of(
                        "code", "tema-1", "name", "Tema Branch", "branchType", "BRANCH",
                        "digitalAddress", "GT-001-0001", "phone", "+233303000001"))
                .expect(201).data();
        String id = created.get("id").asString();
        assertThat(created.get("code").asString()).isEqualTo("TEMA-1");
        assertThat(created.get("status").asString()).isEqualTo("ACTIVE");
        long version = created.get("version").asLong();

        JsonNode updated = api.put("/api/v1/branches/" + id, tenant.adminToken(), Map.of(
                "name", "Tema Community 1", "version", version)).expect(200).data();
        assertThat(updated.get("name").asString()).isEqualTo("Tema Community 1");
        assertThat(updated.get("version").asLong()).isEqualTo(version + 1);

        api.put("/api/v1/branches/" + id, tenant.adminToken(), Map.of("name", "Lost update", "version", version))
                .expectError(409, "CONCURRENT_MODIFICATION");

        JsonNode closed = api.post("/api/v1/branches/" + id + "/status", tenant.adminToken(), Map.of(
                "status", "CLOSED", "reason", "Consolidated", "version", version + 1)).expect(200).data();
        assertThat(closed.get("status").asString()).isEqualTo("CLOSED");
        assertThat(closed.get("closedOn").isNull()).isFalse();

        api.post("/api/v1/branches/" + id + "/status", tenant.adminToken(), Map.of(
                        "status", "ACTIVE", "reason", "Reopen", "version", version + 2))
                .expectError(422, "INVALID_STATE_TRANSITION");

        JsonNode audit = api.get("/api/v1/audit-logs?resourceType=BRANCH&resourceId=" + id, tenant.adminToken())
                .expect(200).data();
        List<String> actions = new ArrayList<>();
        audit.get("items").forEach(item -> actions.add(item.get("action").asString()));
        assertThat(actions).containsExactly("BRANCH_STATUS_CHANGED", "BRANCH_UPDATED", "BRANCH_CREATED");
        JsonNode statusChange = audit.at("/items/0");
        assertThat(statusChange.at("/before/status").asString()).isEqualTo("ACTIVE");
        assertThat(statusChange.at("/after/status").asString()).isEqualTo("CLOSED");
        assertThat(statusChange.at("/metadata/reason").asString()).isEqualTo("Consolidated");
        assertThat(statusChange.get("actorId").asString()).isEqualTo(tenant.adminId().toString());
    }

    @Test
    void invalidInputIsReportedPerField() {
        TenantHandle tenant = fixtures.onboardTenant();
        JsonNode error = api.post("/api/v1/branches", tenant.adminToken(), Map.of("code", "!!"))
                .expectError(400, "VALIDATION_FAILED").body();
        List<String> fields = new ArrayList<>();
        error.get("errors").forEach(violation -> fields.add(violation.get("field").asString()));
        assertThat(fields).contains("code", "name", "branchType");

        api.post("/api/v1/branches", tenant.adminToken(), "{\"code\": ").expectError(400, "MALFORMED_REQUEST");
    }

    @Test
    void branchCodesAreUniqueWithinAnInstitution() {
        TenantHandle tenant = fixtures.onboardTenant();
        fixtures.createBranch(tenant, "DUP");
        api.post("/api/v1/branches", tenant.adminToken(), Map.of("code", "dup", "name", "Again",
                "branchType", "BRANCH")).expectError(409, "DUPLICATE_RESOURCE");
        // the same code is fine in another institution
        fixtures.createBranch(fixtures.onboardTenant(), "DUP");
    }

    @Test
    void headOfficeCannotBeClosed() {
        TenantHandle tenant = fixtures.onboardTenant();
        long version = api.get("/api/v1/branches/" + tenant.headOfficeId(), tenant.adminToken()).expect(200).data()
                .get("version").asLong();
        api.post("/api/v1/branches/" + tenant.headOfficeId() + "/status", tenant.adminToken(), Map.of(
                "status", "CLOSED", "reason", "x", "version", version)).expectError(422, "BUSINESS_RULE_VIOLATION");
    }

    @Test
    void branchScopedStaffSeeOnlyTheirBranchAndPermissionsAreEnforced() {
        TenantHandle tenant = fixtures.onboardTenant();
        UUID kumasi = fixtures.createBranch(tenant, "KSI");
        StaffHandle manager = fixtures.createStaff(tenant, "manager.ksi", kumasi, false, "BRANCH_MANAGER");
        StaffHandle teller = fixtures.createStaff(tenant, "teller.ksi", kumasi, false, "TELLER");

        JsonNode visible = api.get("/api/v1/branches", manager.token()).expect(200).data();
        assertThat(visible.get("totalItems").asLong()).isEqualTo(1);
        assertThat(visible.at("/items/0/id").asString()).isEqualTo(kumasi.toString());
        api.get("/api/v1/branches/" + tenant.headOfficeId(), manager.token()).expectError(404, "RESOURCE_NOT_FOUND");

        // branch managers can view but not manage branches; tellers can't even view them
        api.post("/api/v1/branches", manager.token(), Map.of("code", "NEW", "name", "New", "branchType", "BRANCH"))
                .expectError(403, "ACCESS_DENIED");
        api.get("/api/v1/branches", teller.token()).expectError(403, "ACCESS_DENIED");

        JsonNode denied = api.get("/api/v1/audit-logs?action=ACCESS_DENIED", tenant.adminToken()).expect(200).data();
        assertThat(denied.get("totalItems").asLong()).isEqualTo(2);
    }
}
