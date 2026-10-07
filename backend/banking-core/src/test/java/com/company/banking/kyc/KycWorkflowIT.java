package com.company.banking.kyc;

import com.company.banking.support.Fixtures;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end KYC: capture by an officer, four-eyes review and approval, and the lock on verified identity data.
 */
class KycWorkflowIT extends IntegrationTest {

    @Autowired
    private DataSource dataSource;

    private TenantHandle tenant;
    private StaffHandle officer;
    private StaffHandle manager;
    private StaffHandle compliance;

    @BeforeEach
    void staff() {
        tenant = fixtures.onboardTenant();
        officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        compliance = fixtures.createStaff(tenant, "compliance", tenant.headOfficeId(), true, "COMPLIANCE_OFFICER");
    }

    @Test
    void onboardingRunsFromCaptureThroughFourEyesApprovalToAnActiveCustomer() {
        String customerId = capturedCustomer(officer, "GHA-123456789-0");
        JsonNode opened = openCase(officer, customerId, "ONBOARDING");
        String caseId = opened.get("id").asString();
        assertThat(opened.get("status").asString()).isEqualTo("OPEN");
        assertThat(opened.get("customerKycStatus").asString()).isEqualTo("IN_PROGRESS");

        JsonNode missing = api.post(cases(caseId, "submit"), officer.token(), null)
                .expectError(422, "KYC_REQUIREMENTS_NOT_MET").body();
        assertThat(missing.get("message").asString()).contains("ID_FRONT", "SELFIE");

        String idFront = fixtures.uploadDocument(officer.token(), customerId, "ID_FRONT", Fixtures.PNG);
        String selfie = fixtures.uploadDocument(officer.token(), customerId, "SELFIE", Fixtures.JPEG);
        JsonNode checked = api.post(cases(caseId, "identity-check"), officer.token(), null).expect(200).data();
        assertThat(checked.at("/checks/0/result").asString()).isEqualTo("PASS");
        assertThat(checked.at("/checks/0/provider").asString()).isEqualTo("STUB");

        JsonNode submitted = api.post(cases(caseId, "submit"), officer.token(), null).expect(200).data();
        assertThat(submitted.get("status").asString()).isEqualTo("PENDING_REVIEW");

        // Under review: the record is frozen for the officer, and officers can't decide.
        api.post("/api/v1/customers/" + customerId + "/addresses", officer.token(), Map.of("addressType",
                "MAILING", "line1", "PO Box 1", "countryCode", "GH")).expectError(422, "KYC_UNDER_REVIEW");
        api.post(cases(caseId, "approve"), officer.token(), decision("LOW", submitted))
                .expectError(403, "ACCESS_DENIED");

        // Documents still need acceptance by a reviewer.
        api.post(cases(caseId, "approve"), compliance.token(), decision("LOW", submitted))
                .expectError(422, "KYC_REQUIREMENTS_NOT_MET");
        acceptDocument(compliance, customerId, idFront);
        acceptDocument(compliance, customerId, selfie);

        JsonNode approved = api.post(cases(caseId, "approve"), compliance.token(), decision("LOW", submitted))
                .expect(200).data();
        assertThat(approved.get("status").asString()).isEqualTo("APPROVED");
        assertThat(approved.get("decidedBy").asString()).isEqualTo(compliance.id().toString());

        JsonNode customer = api.get("/api/v1/customers/" + customerId, officer.token()).expect(200).data();
        assertThat(customer.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(customer.get("kycStatus").asString()).isEqualTo("VERIFIED");
        assertThat(customer.get("kycTierCode").asString()).isEqualTo("TIER_2");
        assertThat(customer.get("riskLevel").asString()).isEqualTo("LOW");
        assertThat(customer.at("/identifications/0/verificationStatus").asString()).isEqualTo("VERIFIED");

        assertThat(auditActions(caseId)).contains("KYC_CASE_OPENED", "KYC_CHECK_RECORDED", "KYC_CASE_SUBMITTED",
                "KYC_CASE_APPROVED");
    }

    @Test
    void whoeverSubmitsOrUploadsCannotDecideOrAccept() throws SQLException {
        String customerId = capturedCustomer(manager, "GHA-222333444-0");
        String caseId = openCase(manager, customerId, "ONBOARDING").get("id").asString();
        String idFront = fixtures.uploadDocument(manager.token(), customerId, "ID_FRONT", Fixtures.PNG);
        fixtures.uploadDocument(manager.token(), customerId, "SELFIE", Fixtures.JPEG);
        api.post(cases(caseId, "identity-check"), manager.token(), null).expect(200);
        JsonNode submitted = api.post(cases(caseId, "submit"), manager.token(), null).expect(200).data();

        api.post("/api/v1/customers/" + customerId + "/documents/" + idFront + "/review", manager.token(),
                Map.of("decision", "ACCEPTED")).expectError(403, "FOUR_EYES_VIOLATION");
        api.post(cases(caseId, "approve"), manager.token(), decision("LOW", submitted))
                .expectError(403, "FOUR_EYES_VIOLATION");
        api.post(cases(caseId, "return"), manager.token(), Map.of("note", "self", "version",
                submitted.get("version").asLong())).expectError(403, "FOUR_EYES_VIOLATION");

        // The database refuses it too, even if application code were bypassed.
        assertThatThrownBy(() -> selfApproveWithSql(UUID.fromString(caseId)))
                .isInstanceOf(SQLException.class)
                .extracting(ex -> ((SQLException) ex).getSQLState()).isEqualTo("23514");

        JsonNode returned = api.post(cases(caseId, "return"), compliance.token(), Map.of(
                "note", "Selfie is blurred", "version", submitted.get("version").asLong())).expect(200).data();
        assertThat(returned.get("status").asString()).isEqualTo("RETURNED");
        assertThat(returned.get("customerKycStatus").asString()).isEqualTo("IN_PROGRESS");
        api.post(cases(caseId, "submit"), manager.token(), null).expect(200);
    }

    @Test
    void verifiedIdentityDataChangesOnlyThroughAnUpdateCase() {
        String customerId = verifiedCustomer("GHA-333444555-0");
        JsonNode customer = api.get("/api/v1/customers/" + customerId, officer.token()).expect(200).data();

        api.put("/api/v1/customers/" + customerId, officer.token(), update(customer, "Mensah-Owusu", "+233244111222"))
                .expectError(422, "KYC_DATA_LOCKED");
        JsonNode phoneChanged = api.put("/api/v1/customers/" + customerId, officer.token(),
                update(customer, "Mensah", "+233209999999")).expect(200).data();
        api.post("/api/v1/customers/" + customerId + "/identifications", officer.token(), Map.of("idTypeCode",
                "PASSPORT", "idNumber", "G1234567", "expiryDate", "2030-01-01")).expectError(422, "KYC_DATA_LOCKED");

        api.post("/api/v1/customers/" + customerId + "/kyc-cases", officer.token(), Map.of("caseType", "ONBOARDING",
                "targetTierCode", "TIER_2")).expectError(422, "KYC_CASE_TYPE_NOT_ALLOWED");
        assertThat(phoneChanged.get("primaryPhone").asString()).isEqualTo("+233209999999");
        String caseId = openCase(officer, customerId, "UPDATE").get("id").asString();
        JsonNode reopened = api.get("/api/v1/customers/" + customerId, officer.token()).expect(200).data();
        assertThat(reopened.get("kycStatus").asString()).isEqualTo("IN_PROGRESS");
        JsonNode renamed = api.put("/api/v1/customers/" + customerId, officer.token(),
                update(reopened, "Mensah-Owusu", "+233209999999")).expect(200).data();
        assertThat(renamed.get("displayName").asString()).isEqualTo("Ama Mensah-Owusu");
        assertThat(renamed.get("status").asString()).isEqualTo("ACTIVE");

        // Evidence is per case: the identity is verified again for the changed data.
        api.post(cases(caseId, "identity-check"), officer.token(), null).expect(200);
        JsonNode submitted = api.post(cases(caseId, "submit"), officer.token(), null).expect(200).data();
        api.post(cases(caseId, "approve"), compliance.token(), decision("LOW", submitted)).expect(200);
        assertThat(api.get("/api/v1/customers/" + customerId, officer.token()).expect(200).data()
                .get("kycStatus").asString()).isEqualTo("VERIFIED");
    }

    @Test
    void watchlistHitsForceHighRiskAndIdentityMismatchesNeedManualVerification() {
        String customerId = capturedCustomer(officer, "GHA-444555666-9");
        String caseId = openCase(officer, customerId, "ONBOARDING").get("id").asString();
        String idFront = fixtures.uploadDocument(officer.token(), customerId, "ID_FRONT", Fixtures.PNG);
        String selfie = fixtures.uploadDocument(officer.token(), customerId, "SELFIE", Fixtures.JPEG);
        JsonNode mismatch = api.post(cases(caseId, "identity-check"), officer.token(), null).expect(200).data();
        assertThat(mismatch.at("/checks/0/result").asString()).isEqualTo("FAIL");
        JsonNode submitted = api.post(cases(caseId, "submit"), officer.token(), null).expect(200).data();
        acceptDocument(compliance, customerId, idFront);
        acceptDocument(compliance, customerId, selfie);

        api.post(cases(caseId, "approve"), compliance.token(), decision("LOW", submitted))
                .expectError(422, "KYC_REQUIREMENTS_NOT_MET");
        api.post(cases(caseId, "checks"), compliance.token(), Map.of("checkType", "IDENTITY_VERIFICATION",
                "result", "PASS", "note", "Verified in person against the physical card")).expect(200);
        api.post(cases(caseId, "checks"), compliance.token(), Map.of("checkType", "WATCHLIST",
                "result", "FAIL", "note", "Possible sanctions list match; EDD performed")).expect(200);

        api.post(cases(caseId, "approve"), compliance.token(), decision("MEDIUM", submitted))
                .expectError(422, "HIGH_RISK_REQUIRED");
        api.post(cases(caseId, "approve"), compliance.token(), decision("HIGH", submitted)).expect(200);
        assertThat(api.get("/api/v1/customers/" + customerId, officer.token()).expect(200).data()
                .get("riskLevel").asString()).isEqualTo("HIGH");
    }

    @Test
    void onlyOneOpenCasePerCustomerAndTheQueueIsBranchScoped() {
        String customerId = capturedCustomer(officer, "GHA-555666777-0");
        openCase(officer, customerId, "ONBOARDING");
        api.post("/api/v1/customers/" + customerId + "/kyc-cases", officer.token(), Map.of("caseType", "ONBOARDING",
                "targetTierCode", "TIER_1")).expectError(409, "KYC_CASE_ALREADY_OPEN");

        UUID kumasi = fixtures.createBranch(tenant, "KSI");
        StaffHandle kumasiManager = fixtures.createStaff(tenant, "ksi.manager", kumasi, false, "BRANCH_MANAGER");
        assertThat(api.get("/api/v1/kyc/cases?status=OPEN", compliance.token()).expect(200).data()
                .get("totalItems").asLong()).isEqualTo(1);
        assertThat(api.get("/api/v1/kyc/cases?status=OPEN", kumasiManager.token()).expect(200).data()
                .get("totalItems").asLong()).isZero();
    }

    // ---------------------------------------------------------------------------------------------------------

    /**
     * A customer with identification, address, next of kin and employment data; documents still to upload.
     */
    private String capturedCustomer(StaffHandle staff, String ghanaCard) {
        String customerId = fixtures.createIndividual(staff.token(), tenant.headOfficeId(), "Ama", "Mensah",
                "+233244111222").get("id").asString();
        fixtures.addGhanaCard(staff.token(), customerId, ghanaCard);
        fixtures.addAddress(staff.token(), customerId);
        fixtures.addNextOfKin(staff.token(), customerId);
        return customerId;
    }

    private String verifiedCustomer(String ghanaCard) {
        String customerId = capturedCustomer(officer, ghanaCard);
        String caseId = openCase(officer, customerId, "ONBOARDING").get("id").asString();
        String idFront = fixtures.uploadDocument(officer.token(), customerId, "ID_FRONT", Fixtures.PNG);
        String selfie = fixtures.uploadDocument(officer.token(), customerId, "SELFIE", Fixtures.JPEG);
        api.post(cases(caseId, "identity-check"), officer.token(), null).expect(200);
        JsonNode submitted = api.post(cases(caseId, "submit"), officer.token(), null).expect(200).data();
        acceptDocument(compliance, customerId, idFront);
        acceptDocument(compliance, customerId, selfie);
        api.post(cases(caseId, "approve"), compliance.token(), decision("LOW", submitted)).expect(200);
        return customerId;
    }

    private JsonNode openCase(StaffHandle staff, String customerId, String caseType) {
        return api.post("/api/v1/customers/" + customerId + "/kyc-cases", staff.token(), Map.of(
                "caseType", caseType, "targetTierCode", "TIER_2")).expect(201).data();
    }

    private void acceptDocument(StaffHandle reviewer, String customerId, String documentId) {
        api.post("/api/v1/customers/" + customerId + "/documents/" + documentId + "/review", reviewer.token(),
                Map.of("decision", "ACCEPTED")).expect(200);
    }

    private static Map<String, Object> decision(String riskLevel, JsonNode kycCase) {
        return Map.of("riskLevel", riskLevel, "note", "Checked", "version", kycCase.get("version").asLong());
    }

    private static String cases(String caseId, String action) {
        return "/api/v1/kyc/cases/" + caseId + "/" + action;
    }

    private static Map<String, Object> update(JsonNode customer, String lastName, String phone) {
        Map<String, Object> individual = new LinkedHashMap<>();
        individual.put("firstName", "Ama");
        individual.put("lastName", lastName);
        individual.put("dateOfBirth", "1990-04-12");
        individual.put("gender", "FEMALE");
        individual.put("nationality", "GH");
        individual.put("occupation", "Trader");
        individual.put("employmentStatus", "SELF_EMPLOYED");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("primaryPhone", phone);
        request.put("individual", individual);
        request.put("version", customer.get("version").asLong());
        return request;
    }

    private List<String> auditActions(String caseId) {
        List<String> actions = new ArrayList<>();
        api.get("/api/v1/audit-logs?resourceType=KYC_CASE&resourceId=" + caseId, tenant.adminToken()).expect(200)
                .data().get("items").forEach(item -> actions.add(item.get("action").asString()));
        return actions;
    }

    private void selfApproveWithSql(UUID caseId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement setTenant = connection.prepareStatement(
                        "SELECT set_config('app.tenant_id', ?, true)")) {
                    setTenant.setString(1, tenant.id().toString());
                    setTenant.execute();
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE core.kyc_case SET status = 'APPROVED', decided_by = submitted_by, decided_at = now()
                        WHERE id = ?""")) {
                    update.setObject(1, caseId);
                    update.executeUpdate();
                }
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }
    }
}
