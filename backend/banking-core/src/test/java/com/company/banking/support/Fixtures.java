package com.company.banking.support;

import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Builds test scenarios through the public API only, exactly as real clients would.
 */
public final class Fixtures {

    public static final String STAFF_PASSWORD = "Correct-Horse-Battery-9";

    private final Api api;

    Fixtures(Api api) {
        this.api = api;
    }

    public String platformToken() {
        return api.post("/api/v1/platform/auth/login", null, Map.of(
                        "username", IntegrationTest.PLATFORM_USERNAME,
                        "password", IntegrationTest.PLATFORM_PASSWORD))
                .expect(200).data().get("accessToken").asString();
    }

    /**
     * Onboards a fresh institution and signs its administrator in (temporary password already changed).
     */
    public TenantHandle onboardTenant() {
        String code = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JsonNode onboarded = api.post("/api/v1/platform/tenants", platformToken(), onboardingRequest(code))
                .expect(201).data();
        String temporaryPassword = onboarded.at("/administratorCredential/temporaryPassword").asString();
        Tokens temporary = login(code, "admin", temporaryPassword);
        String adminToken = changePassword(temporary.accessToken(), temporaryPassword, STAFF_PASSWORD).accessToken();

        Map<String, UUID> roles = new HashMap<>();
        api.get("/api/v1/roles", adminToken).expect(200).data()
                .forEach(role -> roles.put(role.get("code").asString(), UUID.fromString(role.get("id").asString())));
        return new TenantHandle(code,
                UUID.fromString(onboarded.at("/tenant/id").asString()),
                UUID.fromString(onboarded.at("/headOffice/id").asString()),
                UUID.fromString(onboarded.get("administratorId").asString()),
                adminToken, roles);
    }

    public static Map<String, Object> onboardingRequest(String code) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("code", code);
        request.put("legalName", "Test Institution " + code + " Ltd");
        request.put("displayName", "Test Institution " + code);
        request.put("institutionType", "MICROFINANCE");
        request.put("countryCode", "GH");
        request.put("baseCurrency", "GHS");
        request.put("timezone", "Africa/Accra");
        request.put("locale", "en-GH");
        request.put("contactEmail", "info@" + code + ".test");
        request.put("headOffice", Map.of("code", "HQ", "name", "Head Office", "city", "Accra"));
        request.put("administrator", Map.of("firstName", "Ada", "lastName", "Admin",
                "email", "admin@" + code + ".test", "username", "admin"));
        request.put("features", List.of("SAVINGS", "LOANS"));
        return request;
    }

    public UUID createBranch(TenantHandle tenant, String code) {
        JsonNode branch = api.post("/api/v1/branches", tenant.adminToken(), Map.of(
                        "code", code, "name", code + " Branch", "branchType", "BRANCH"))
                .expect(201).data();
        return UUID.fromString(branch.get("id").asString());
    }

    /**
     * Creates a staff member with the given default roles and signs them in with a permanent password.
     */
    public StaffHandle createStaff(TenantHandle tenant, String username, UUID branchId, boolean allBranches,
                                   String... roleCodes) {
        Set<String> roleIds = Set.of(roleCodes).stream()
                .map(code -> tenant.roles().get(code).toString())
                .collect(Collectors.toSet());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("employeeNumber", "E-" + username.toUpperCase());
        request.put("firstName", "Test");
        request.put("lastName", username);
        request.put("email", username + "@" + tenant.code() + ".test");
        request.put("homeBranchId", branchId.toString());
        request.put("allBranchesAccess", allBranches);
        request.put("username", username);
        request.put("roleIds", roleIds);
        JsonNode created = api.post("/api/v1/staff", tenant.adminToken(), request).expect(201).data();
        String temporaryPassword = created.at("/credential/temporaryPassword").asString();
        Tokens temporary = login(tenant.code(), username, temporaryPassword);
        Tokens tokens = changePassword(temporary.accessToken(), temporaryPassword, STAFF_PASSWORD);
        return new StaffHandle(UUID.fromString(created.at("/staff/id").asString()), username, tokens);
    }

    /**
     * Minimal content with a real PNG/JPEG signature (the server sniffs file types by their leading bytes).
     */
    public static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5};
    public static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 9, 8, 7, 6};

    /**
     * Registers an individual customer and returns the API representation.
     */
    public JsonNode createIndividual(String token, UUID branchId, String firstName, String lastName, String phone) {
        Map<String, Object> individual = new LinkedHashMap<>();
        individual.put("firstName", firstName);
        individual.put("lastName", lastName);
        individual.put("dateOfBirth", "1990-04-12");
        individual.put("gender", "FEMALE");
        individual.put("nationality", "GH");
        individual.put("occupation", "Trader");
        individual.put("employmentStatus", "SELF_EMPLOYED");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("customerType", "INDIVIDUAL");
        request.put("homeBranchId", branchId.toString());
        request.put("primaryPhone", phone);
        request.put("individual", individual);
        return api.post("/api/v1/customers", token, request).expect(201).data();
    }

    /**
     * An ACTIVE individual customer at {@code branchId} with KYC tier 2: captured by {@code officer} and approved by
     * {@code reviewer} (four eyes), the same way staff onboard a customer.
     */
    public UUID verifiedIndividual(StaffHandle officer, StaffHandle reviewer, UUID branchId, String firstName) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String customerId = createIndividual(officer.token(), branchId, firstName, "Mensah",
                "+23324" + (1_000_000 + random.nextInt(8_999_999))).get("id").asString();
        // The stub identity provider fails numbers ending in 9.
        addGhanaCard(officer.token(), customerId,
                "GHA-" + (100_000_000 + random.nextInt(899_999_999)) + "-" + random.nextInt(9));
        addAddress(officer.token(), customerId);
        addNextOfKin(officer.token(), customerId);
        String caseId = api.post("/api/v1/customers/" + customerId + "/kyc-cases", officer.token(), Map.of(
                "caseType", "ONBOARDING", "targetTierCode", "TIER_2")).expect(201).data().get("id").asString();
        List<String> documents = List.of(uploadDocument(officer.token(), customerId, "ID_FRONT", PNG),
                uploadDocument(officer.token(), customerId, "SELFIE", JPEG));
        api.post("/api/v1/kyc/cases/" + caseId + "/identity-check", officer.token(), null).expect(200);
        JsonNode submitted = api.post("/api/v1/kyc/cases/" + caseId + "/submit", officer.token(), null)
                .expect(200).data();
        for (String documentId : documents) {
            api.post("/api/v1/customers/" + customerId + "/documents/" + documentId + "/review", reviewer.token(),
                    Map.of("decision", "ACCEPTED")).expect(200);
        }
        api.post("/api/v1/kyc/cases/" + caseId + "/approve", reviewer.token(), Map.of(
                "riskLevel", "LOW", "note", "Checked", "version", submitted.get("version").asLong())).expect(200);
        return UUID.fromString(customerId);
    }

    public JsonNode addGhanaCard(String token, String customerId, String number) {
        return api.post("/api/v1/customers/" + customerId + "/identifications", token, Map.of(
                "idTypeCode", "GHANA_CARD", "idNumber", number, "issuingCountry", "GH",
                "expiryDate", "2031-01-01")).expect(201).data();
    }

    public void addAddress(String token, String customerId) {
        api.post("/api/v1/customers/" + customerId + "/addresses", token, Map.of(
                "addressType", "RESIDENTIAL", "line1", "12 Market Street", "city", "Accra",
                "countryCode", "GH", "digitalAddress", "GA-123-4567")).expect(201);
    }

    public void addNextOfKin(String token, String customerId) {
        api.post("/api/v1/customers/" + customerId + "/next-of-kin", token, Map.of(
                "fullName", "Kojo Mensah", "relationship", "Brother", "phone", "+233244000111",
                "primary", true)).expect(201);
    }

    public String uploadDocument(String token, String customerId, String documentType, byte[] content) {
        return api.upload("/api/v1/customers/" + customerId + "/documents", token, "scan.bin", content,
                "documentType", documentType).expect(201).data().get("id").asString();
    }

    public Tokens login(String tenantCode, String username, String password) {
        JsonNode data = api.post("/api/v1/auth/staff/login", null, Map.of(
                "tenantCode", tenantCode, "username", username, "password", password)).expect(200).data();
        return Tokens.from(data);
    }

    public Tokens changePassword(String accessToken, String currentPassword, String newPassword) {
        JsonNode data = api.post("/api/v1/auth/password", accessToken, Map.of(
                "currentPassword", currentPassword, "newPassword", newPassword)).expect(200).data();
        return Tokens.from(data);
    }

    public record TenantHandle(String code, UUID id, UUID headOfficeId, UUID adminId, String adminToken,
                               Map<String, UUID> roles) {
    }

    public record StaffHandle(UUID id, String username, Tokens tokens) {

        public String token() {
            return tokens.accessToken();
        }
    }

    public record Tokens(String accessToken, String refreshToken, boolean passwordChangeRequired) {

        static Tokens from(JsonNode data) {
            return new Tokens(data.get("accessToken").asString(), data.get("refreshToken").asString(),
                    data.get("passwordChangeRequired").asBoolean());
        }
    }
}
