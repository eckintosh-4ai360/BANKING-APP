package com.company.banking.customer;

import com.company.banking.common.sequence.CheckDigits;
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

class CustomerApiIT extends IntegrationTest {

    @Test
    void registersCustomersWithCheckDigitNumbersPendingKyc() {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");

        JsonNode first = fixtures.createIndividual(officer.token(), tenant.headOfficeId(), "Ama", "Mensah",
                "+233244111222");
        JsonNode second = fixtures.createIndividual(officer.token(), tenant.headOfficeId(), "Kofi", "Boateng",
                "+233244333444");

        String number = first.get("customerNumber").asString();
        assertThat(number).hasSize(10).startsWith("000000001");
        assertThat(CheckDigits.isValidLuhn(number)).isTrue();
        assertThat(second.get("customerNumber").asString()).startsWith("000000002");
        assertThat(first.get("displayName").asString()).isEqualTo("Ama Mensah");
        assertThat(first.get("status").asString()).isEqualTo("PENDING");
        assertThat(first.get("kycStatus").asString()).isEqualTo("NOT_STARTED");
        assertThat(first.get("riskLevel").asString()).isEqualTo("UNASSESSED");
    }

    @Test
    void identityNumbersAreFormatCheckedMaskedUniqueAndRevealedOnlyWithAudit() {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        StaffHandle compliance = fixtures.createStaff(tenant, "compliance", tenant.headOfficeId(), true,
                "COMPLIANCE_OFFICER");
        String customerId = fixtures.createIndividual(officer.token(), tenant.headOfficeId(), "Ama", "Mensah",
                "+233244111222").get("id").asString();
        String path = "/api/v1/customers/" + customerId + "/identifications";

        api.post(path, officer.token(), Map.of("idTypeCode", "GHANA_CARD", "idNumber", "12345",
                "expiryDate", "2031-01-01")).expectError(422, "INVALID_IDENTIFICATION_NUMBER");
        api.post(path, officer.token(), Map.of("idTypeCode", "GHANA_CARD", "idNumber", "GHA-123456789-0"))
                .expectError(422, "IDENTIFICATION_EXPIRY_REQUIRED");
        api.post(path, officer.token(), Map.of("idTypeCode", "GHANA_CARD", "idNumber", "GHA-123456789-0",
                "expiryDate", "2020-01-01")).expectError(422, "IDENTIFICATION_EXPIRED");
        api.post(path, officer.token(), Map.of("idTypeCode", "BUSINESS_REGISTRATION", "idNumber", "CS123456789"))
                .expectError(422, "IDENTIFICATION_TYPE_NOT_ACCEPTED");

        JsonNode added = fixtures.addGhanaCard(officer.token(), customerId, " gha-123456789-0 ");
        assertThat(added.get("idNumberMasked").asString()).isEqualTo("***-******789-0");
        assertThat(added.get("primary").asBoolean()).isTrue();
        assertThat(added.has("idNumber")).isFalse();

        String otherId = fixtures.createIndividual(officer.token(), tenant.headOfficeId(), "Ama", "Mensah",
                "+233244111222").get("id").asString();
        String customerNumber = api.get("/api/v1/customers/" + customerId, officer.token()).expect(200).data()
                .get("customerNumber").asString();
        JsonNode duplicate = api.post("/api/v1/customers/" + otherId + "/identifications", officer.token(),
                Map.of("idTypeCode", "GHANA_CARD", "idNumber", "GHA-123456789-0", "expiryDate", "2031-01-01"))
                .expectError(409, "DUPLICATE_IDENTIFICATION").body();
        assertThat(duplicate.get("message").asString()).contains(customerNumber);

        String reveal = path + "/" + added.get("id").asString() + "/number";
        api.get(reveal, officer.token()).expectError(403, "ACCESS_DENIED");
        JsonNode revealed = api.get(reveal, compliance.token()).expect(200).data();
        assertThat(revealed.get("idNumber").asString()).isEqualTo("GHA-123456789-0");

        JsonNode audit = api.get("/api/v1/audit-logs?action=CUSTOMER_IDENTIFICATION_REVEALED", tenant.adminToken())
                .expect(200).data();
        assertThat(audit.get("totalItems").asLong()).isEqualTo(1);
        assertThat(audit.at("/items/0/actorId").asString()).isEqualTo(compliance.id().toString());
        assertThat(audit.toString()).doesNotContain("123456789");
    }

    @Test
    void searchMatchesNamePhoneAndCustomerNumber() {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        JsonNode ama = fixtures.createIndividual(officer.token(), tenant.headOfficeId(), "Ama", "Mensah",
                "+233244111222");
        fixtures.createIndividual(officer.token(), tenant.headOfficeId(), "Kofi", "Boateng", "+233200999888");

        assertThat(names("q=mensa", officer)).containsExactly("Ama Mensah");
        assertThat(names("q=4111222", officer)).containsExactly("Ama Mensah");
        assertThat(names("q=" + ama.get("customerNumber").asString(), officer)).containsExactly("Ama Mensah");
        assertThat(names("q=nobody", officer)).isEmpty();
        assertThat(names("status=PENDING", officer)).containsExactly("Ama Mensah", "Kofi Boateng");
        api.get("/api/v1/customers?q=a", officer.token()).expectError(400, "VALIDATION_FAILED");
    }

    @Test
    void branchScopedStaffSeeAndRegisterOnlyTheirBranchCustomers() {
        TenantHandle tenant = fixtures.onboardTenant();
        UUID kumasi = fixtures.createBranch(tenant, "KSI");
        StaffHandle accraOfficer = fixtures.createStaff(tenant, "accra.officer", tenant.headOfficeId(), false,
                "LOAN_OFFICER");
        StaffHandle kumasiOfficer = fixtures.createStaff(tenant, "kumasi.officer", kumasi, false, "LOAN_OFFICER");
        String kumasiCustomer = fixtures.createIndividual(kumasiOfficer.token(), kumasi, "Yaw", "Osei",
                "+233244555666").get("id").asString();

        api.get("/api/v1/customers/" + kumasiCustomer, accraOfficer.token()).expectError(404, "RESOURCE_NOT_FOUND");
        assertThat(names("q=osei", accraOfficer)).isEmpty();
        assertThat(names("q=osei", kumasiOfficer)).containsExactly("Yaw Osei");

        Map<String, Object> elsewhere = individualRequest(kumasi);
        api.post("/api/v1/customers", accraOfficer.token(), elsewhere).expectError(404, "RESOURCE_NOT_FOUND");
    }

    @Test
    void customersAreInvisibleToOtherInstitutions() {
        TenantHandle a = fixtures.onboardTenant();
        TenantHandle b = fixtures.onboardTenant();
        StaffHandle officerA = fixtures.createStaff(a, "officer", a.headOfficeId(), true, "LOAN_OFFICER");
        StaffHandle officerB = fixtures.createStaff(b, "officer", b.headOfficeId(), true, "LOAN_OFFICER");
        String customerA = fixtures.createIndividual(officerA.token(), a.headOfficeId(), "Ama", "Mensah",
                "+233244111222").get("id").asString();
        fixtures.addGhanaCard(officerA.token(), customerA, "GHA-123456789-0");

        api.get("/api/v1/customers/" + customerA, officerB.token()).expectError(404, "RESOURCE_NOT_FOUND");
        api.post("/api/v1/customers/" + customerA + "/addresses", officerB.token(), Map.of("addressType",
                "RESIDENTIAL", "line1", "x", "countryCode", "GH")).expectError(404, "RESOURCE_NOT_FOUND");
        assertThat(names("q=mensah", officerB)).isEmpty();

        // The same identity document may exist at another institution: blind indexes are per institution.
        String customerB = fixtures.createIndividual(officerB.token(), b.headOfficeId(), "Ama", "Mensah",
                "+233244111222").get("id").asString();
        fixtures.addGhanaCard(officerB.token(), customerB, "GHA-123456789-0");
    }

    @Test
    void profileMustMatchTheCustomerTypeAndBusinessesAreUnique() {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");

        Map<String, Object> mismatch = individualRequest(tenant.headOfficeId());
        mismatch.put("customerType", "BUSINESS");
        api.post("/api/v1/customers", officer.token(), mismatch).expectError(422, "CUSTOMER_PROFILE_MISMATCH");

        Map<String, Object> missingBirthDate = individualRequest(tenant.headOfficeId());
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) missingBirthDate.get("individual");
        details.remove("dateOfBirth");
        api.post("/api/v1/customers", officer.token(), missingBirthDate).expectError(400, "VALIDATION_FAILED");

        Map<String, Object> business = Map.of("customerType", "BUSINESS",
                "homeBranchId", tenant.headOfficeId().toString(),
                "business", Map.of("registeredName", "Makola Traders Ltd", "registrationNumber", "cs-123456",
                        "businessType", "LIMITED_COMPANY", "industrySector", "Retail"));
        String businessId = api.post("/api/v1/customers", officer.token(), business).expect(201).data()
                .get("id").asString();
        Map<String, Object> sameRegistration = new LinkedHashMap<>(business);
        sameRegistration.put("business", Map.of("registeredName", "Other Name", "registrationNumber", "CS-123456",
                "businessType", "PARTNERSHIP"));
        api.post("/api/v1/customers", officer.token(), sameRegistration)
                .expectError(409, "DUPLICATE_BUSINESS_REGISTRATION");

        api.post("/api/v1/customers/" + businessId + "/related-parties", officer.token(), Map.of(
                "fullName", "Akua Owner", "role", "BENEFICIAL_OWNER", "ownershipPercent", "60.00",
                "idTypeCode", "GHANA_CARD", "idNumber", "GHA-111222333-4")).expect(201);
        String individualId = fixtures.createIndividual(officer.token(), tenant.headOfficeId(), "Ama", "Mensah",
                "+233244111222").get("id").asString();
        api.post("/api/v1/customers/" + individualId + "/related-parties", officer.token(), Map.of(
                "fullName", "X", "role", "DIRECTOR")).expectError(422, "CUSTOMER_PROFILE_MISMATCH");
    }

    @Test
    void contactChangesAreVersionedAndAudited() {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        JsonNode customer = fixtures.createIndividual(officer.token(), tenant.headOfficeId(), "Ama", "Mensah",
                "+233244111222");
        String id = customer.get("id").asString();
        long version = customer.get("version").asLong();

        Map<String, Object> update = individualRequest(tenant.headOfficeId());
        update.remove("customerType");
        update.remove("homeBranchId");
        update.put("primaryPhone", "+233209876543");
        update.put("version", version);
        JsonNode updated = api.put("/api/v1/customers/" + id, officer.token(), update).expect(200).data();
        assertThat(updated.get("primaryPhone").asString()).isEqualTo("+233209876543");
        assertThat(updated.get("version").asLong()).isGreaterThan(version);
        api.put("/api/v1/customers/" + id, officer.token(), update).expectError(409, "CONCURRENT_MODIFICATION");

        JsonNode audit = api.get("/api/v1/audit-logs?action=CUSTOMER_UPDATED&resourceId=" + id, tenant.adminToken())
                .expect(200).data();
        assertThat(audit.at("/items/0/before/primaryPhone").asString()).isEqualTo("+233244111222");
        assertThat(audit.at("/items/0/after/primaryPhone").asString()).isEqualTo("+233209876543");
    }

    private List<String> names(String query, StaffHandle staff) {
        List<String> names = new ArrayList<>();
        api.get("/api/v1/customers?" + query, staff.token()).expect(200).data().get("items")
                .forEach(item -> names.add(item.get("displayName").asString()));
        return names;
    }

    private static Map<String, Object> individualRequest(UUID branchId) {
        Map<String, Object> individual = new LinkedHashMap<>();
        individual.put("firstName", "Ama");
        individual.put("lastName", "Mensah");
        individual.put("dateOfBirth", "1990-04-12");
        individual.put("gender", "FEMALE");
        individual.put("nationality", "GH");
        individual.put("employmentStatus", "SELF_EMPLOYED");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("customerType", "INDIVIDUAL");
        request.put("homeBranchId", branchId.toString());
        request.put("individual", individual);
        return request;
    }
}
