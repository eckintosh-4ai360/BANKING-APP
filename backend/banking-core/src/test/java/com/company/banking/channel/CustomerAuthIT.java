package com.company.banking.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.notification.service.StubSmsGateway;
import com.company.banking.support.Api;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Customer sign-in for the mobile app: activation by an existing customer, sign-in on trusted and new devices (a
 * texted code trusts a device), token refresh, device and session management, lockout, the transaction PIN and the
 * password reset, and answers that do not reveal who is a customer.
 */
class CustomerAuthIT extends IntegrationTest {

    private static final Pattern CODE = Pattern.compile("is (\\d{6})");
    private static final String PASSWORD = "Kente-Weaver-2027";
    private static final String PIN = "2580";

    @Autowired
    private StubSmsGateway sms;

    private TenantHandle tenant;
    private StaffHandle manager;
    private String customerNumber;
    private String phone;
    private String nationalPhone;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        fixtures.enableFeature(tenant, "CUSTOMER_MOBILE_APP");
        StaffHandle lender = fixtures.createStaff(tenant, "lender", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        UUID customerId = fixtures.verifiedIndividual(lender, manager, tenant.headOfficeId(), "Abena");
        JsonNode customer = api.get("/api/v1/customers/" + customerId, manager.token()).expect(200).data();
        customerNumber = customer.get("customerNumber").asString();
        phone = customer.get("primaryPhone").asString();
        nationalPhone = "0" + phone.substring(4);
    }

    @Test
    void anExistingCustomerSetsUpMobileBankingAndSignsInOnTrustedAndNewDevices() {
        String phoneA = "device-a-" + UUID.randomUUID();
        JsonNode sent = api.fromDevice("POST", "/api/v1/customer/auth/activation", null, phoneA, Map.of(
                "institutionCode", tenant.code(), "customerNumber", customerNumber, "phoneNumber", nationalPhone))
                .expect(202).data();
        assertThat(sent.get("sentTo").asString()).endsWith(phone.substring(phone.length() - 3)).contains("*");
        String token = sent.get("challengeToken").asString();

        api.fromDevice("POST", "/api/v1/customer/auth/activation/complete", null, phoneA,
                completion(token, "000000", PIN)).expectError(422, "CODE_INVALID");
        api.fromDevice("POST", "/api/v1/customer/auth/activation/complete", null, phoneA,
                completion(token, lastCode(), "1234")).expectError(422, "WEAK_PIN");
        JsonNode tokens = api.fromDevice("POST", "/api/v1/customer/auth/activation/complete", null, phoneA,
                completion(token, lastCode(), PIN)).expect(200).data();
        String accessA = tokens.get("accessToken").asString();
        assertThat(lastMessage()).contains("mobile banking is now set up");

        JsonNode me = api.fromDevice("GET", "/api/v1/customer/me", accessA, phoneA, null).expect(200).data();
        assertThat(me.get("customerNumber").asString()).isEqualTo(customerNumber);
        assertThat(me.get("phoneNumber").asString()).isEqualTo(phone);
        api.get("/api/v1/customers", accessA).expectError(403, "ACCESS_DENIED");
        api.get("/api/v1/customer/me", manager.token()).expectError(403, "ACCESS_DENIED");

        // The trusted device signs in directly; another one needs a texted code, answered from that device.
        JsonNode again = login(phoneA, PASSWORD).expect(200).data();
        assertThat(again.get("accessToken").asString()).isNotBlank();
        String phoneB = "device-b-" + UUID.randomUUID();
        JsonNode challenge = login(phoneB, PASSWORD).expect(200).data();
        assertThat(challenge.get("mfaRequired").asBoolean()).isTrue();
        assertThat(challenge.hasNonNull("accessToken")).as("no tokens before the device is trusted").isFalse();
        String code = lastCode();
        api.fromDevice("POST", "/api/v1/customer/auth/device/verify", null, phoneA, Map.of(
                "challengeToken", challenge.get("mfaChallengeToken").asString(), "code", code))
                .expectError(422, "CODE_INVALID");
        JsonNode tokensB = api.fromDevice("POST", "/api/v1/customer/auth/device/verify", null, phoneB, Map.of(
                "challengeToken", challenge.get("mfaChallengeToken").asString(), "code", code,
                "deviceName", "Tecno Spark")).expect(200).data();
        assertThat(lastMessage()).contains("a new device (Tecno Spark) was added");
        String accessB = tokensB.get("accessToken").asString();

        JsonNode devices = api.fromDevice("GET", "/api/v1/customer/security/devices", accessB, phoneB, null)
                .expect(200).data();
        assertThat(devices).hasSize(2);
        assertThat(devices.get(0).get("name").asString()).isEqualTo("Tecno Spark");
        assertThat(devices.get(0).get("current").asBoolean()).isTrue();

        // Refresh rotates the token; a used refresh token ends the session.
        JsonNode refreshed = api.post("/api/v1/auth/token/refresh", null, Map.of("refreshToken",
                tokensB.get("refreshToken").asString())).expect(200).data();
        assertThat(refreshed.get("refreshToken").asString()).startsWith("c." + tenant.id() + ".");
        accessB = refreshed.get("accessToken").asString();

        // Removing device A ends its sessions at once.
        String deviceA = devices.get(1).get("id").asString();
        api.fromDevice("DELETE", "/api/v1/customer/security/devices/" + deviceA, accessB, phoneB, null)
                .expect(200);
        api.fromDevice("GET", "/api/v1/customer/me", accessA, phoneA, null).expectError(401, "SESSION_REVOKED");
        api.post("/api/v1/auth/token/refresh", null, Map.of("refreshToken", tokens.get("refreshToken").asString()))
                .expectError(401, "INVALID_REFRESH_TOKEN");
        assertThat(login(phoneA, PASSWORD).expect(200).data().get("mfaRequired").asBoolean())
                .as("no longer trusted").isTrue();

        JsonNode sessions = api.fromDevice("GET", "/api/v1/customer/security/sessions", accessB, phoneB, null)
                .expect(200).data();
        assertThat(sessions).extracting(session -> session.get("status").asString()).contains("ACTIVE", "REVOKED");
        assertThat(sessions).filteredOn(session -> session.get("current").asBoolean()).hasSize(1);

        api.fromDevice("POST", "/api/v1/auth/logout", accessB, phoneB, null).expect(200);
        api.fromDevice("GET", "/api/v1/customer/me", accessB, phoneB, null).expectError(401, "SESSION_REVOKED");
    }

    @Test
    void answersDoNotRevealCustomersAndWrongPasswordsLockTheSignIn() {
        String device = "device-" + UUID.randomUUID();
        JsonNode decoy = api.fromDevice("POST", "/api/v1/customer/auth/activation", null, device, Map.of(
                "institutionCode", tenant.code(), "customerNumber", "NOPE-0001", "phoneNumber", nationalPhone))
                .expect(202).data();
        assertThat(decoy.get("sentTo").asString()).contains("*");
        assertThat(sms.sentTo(phone)).as("nothing is texted for a mismatch").isEmpty();
        for (String guess : List.of("000000", "123456")) {
            api.fromDevice("POST", "/api/v1/customer/auth/activation/complete", null, device,
                    completion(decoy.get("challengeToken").asString(), guess, PIN)).expectError(422, "CODE_INVALID");
        }

        activate(device);
        api.fromDevice("POST", "/api/v1/customer/auth/activation", null, device, Map.of("institutionCode",
                tenant.code(), "customerNumber", customerNumber, "phoneNumber", phone)).expect(202);
        assertThat(lastMessage()).as("the owner is told, the requester learns nothing")
                .contains("already set up").doesNotContain("your code");

        login(device, PASSWORD + "x").expectError(401, "INVALID_CREDENTIALS");
        api.fromDevice("POST", "/api/v1/customer/auth/login", null, device, Map.of("institutionCode", tenant.code(),
                "phoneNumber", "0209999999", "password", PASSWORD)).expectError(401, "INVALID_CREDENTIALS");
        api.fromDevice("POST", "/api/v1/customer/auth/login", null, null, Map.of("institutionCode", tenant.code(),
                "phoneNumber", phone, "password", PASSWORD)).expectError(400, "DEVICE_ID_REQUIRED");
        for (int attempt = 2; attempt <= 4; attempt++) {
            login(device, PASSWORD + attempt).expectError(401, "INVALID_CREDENTIALS");
        }
        login(device, PASSWORD + "5").expectError(423, "ACCOUNT_LOCKED");
        assertThat(lastMessage()).contains("locked after several wrong passwords");
        login(device, PASSWORD).expectError(423, "ACCOUNT_LOCKED");

        TenantHandle other = fixtures.onboardTenant();
        api.fromDevice("POST", "/api/v1/customer/auth/login", null, device, Map.of("institutionCode", other.code(),
                "phoneNumber", phone, "password", PASSWORD)).expectError(403, "CHANNEL_NOT_ENABLED");
    }

    @Test
    void theTransactionPinLocksAndIsResetWithACodeAndThePassword() {
        String device = "device-" + UUID.randomUUID();
        String access = activate(device).get("accessToken").asString();
        for (int attempt = 1; attempt <= 4; attempt++) {
            Api.Response wrong = api.fromDevice("PUT", "/api/v1/customer/security/pin", access, device, Map.of(
                    "currentPin", "9753", "newPin", "4826")).expectError(422, "WRONG_PIN");
            assertThat(wrong.body().get("message").asString()).contains((5 - attempt) + " attempt");
        }
        api.fromDevice("PUT", "/api/v1/customer/security/pin", access, device, Map.of("currentPin", "9753",
                "newPin", "4826")).expectError(423, "PIN_LOCKED");
        api.fromDevice("PUT", "/api/v1/customer/security/pin", access, device, Map.of("currentPin", PIN,
                "newPin", "4826")).expectError(423, "PIN_LOCKED");
        assertThat(api.fromDevice("GET", "/api/v1/customer/me", access, device, null).expect(200).data()
                .get("pinLocked").asBoolean()).isTrue();

        String reset = api.fromDevice("POST", "/api/v1/customer/security/pin/reset", access, device, null)
                .expect(202).data().get("challengeToken").asString();
        String code = lastCode();
        api.fromDevice("POST", "/api/v1/customer/security/pin/reset/complete", access, device, Map.of(
                "challengeToken", reset, "code", code, "password", PASSWORD + "x", "newPin", "4826"))
                .expectError(401, "INVALID_CREDENTIALS");
        reset = api.fromDevice("POST", "/api/v1/customer/security/pin/reset", access, device, null)
                .expect(202).data().get("challengeToken").asString();
        api.fromDevice("POST", "/api/v1/customer/security/pin/reset/complete", access, device, Map.of(
                "challengeToken", reset, "code", lastCode(), "password", PASSWORD, "newPin", "4826")).expect(200);
        assertThat(lastMessage()).contains("transaction PIN was reset");
        api.fromDevice("PUT", "/api/v1/customer/security/pin", access, device, Map.of("currentPin", "4826",
                "newPin", "7319")).expect(200);
    }

    @Test
    void aForgottenPasswordIsResetWithACodeAndTheTransactionPin() {
        String device = "device-" + UUID.randomUUID();
        String oldAccess = activate(device).get("accessToken").asString();
        String reset = startPasswordReset(device);
        api.fromDevice("POST", "/api/v1/customer/auth/password/reset/complete", null, device, Map.of(
                "challengeToken", reset, "code", lastCode(), "pin", "9753", "newPassword", "Adinkra-Symbol-55"))
                .expectError(422, "WRONG_PIN");
        reset = startPasswordReset(device);
        api.fromDevice("POST", "/api/v1/customer/auth/password/reset/complete", null, device, Map.of(
                "challengeToken", reset, "code", lastCode(), "pin", PIN, "newPassword", "short"))
                .expect(422);
        api.fromDevice("POST", "/api/v1/customer/auth/password/reset/complete", null, device, Map.of(
                "challengeToken", reset, "code", lastCode(), "pin", PIN, "newPassword", "Adinkra-Symbol-55"))
                .expect(200);
        api.fromDevice("GET", "/api/v1/customer/me", oldAccess, device, null).expectError(401, "SESSION_REVOKED");
        login(device, PASSWORD).expectError(401, "INVALID_CREDENTIALS");
        String access = login(device, "Adinkra-Symbol-55").expect(200).data().get("accessToken").asString();

        JsonNode changed = api.fromDevice("POST", "/api/v1/customer/auth/password", access, device, Map.of(
                "currentPassword", "Adinkra-Symbol-55", "newPassword", "Kwame-Nkrumah-Circle-9")).expect(200).data();
        api.fromDevice("GET", "/api/v1/customer/me", access, device, null).expectError(401, "SESSION_REVOKED");
        api.fromDevice("GET", "/api/v1/customer/me", changed.get("accessToken").asString(), device, null)
                .expect(200);
    }

    // ---------------------------------------------------------------------------------------------------------

    private JsonNode activate(String device) {
        String token = api.fromDevice("POST", "/api/v1/customer/auth/activation", null, device, Map.of(
                "institutionCode", tenant.code(), "customerNumber", customerNumber, "phoneNumber", phone))
                .expect(202).data().get("challengeToken").asString();
        return api.fromDevice("POST", "/api/v1/customer/auth/activation/complete", null, device,
                completion(token, lastCode(), PIN)).expect(200).data();
    }

    private String startPasswordReset(String device) {
        return api.fromDevice("POST", "/api/v1/customer/auth/password/reset", null, device, Map.of(
                "institutionCode", tenant.code(), "phoneNumber", nationalPhone)).expect(202).data()
                .get("challengeToken").asString();
    }

    private Api.Response login(String device, String password) {
        return api.fromDevice("POST", "/api/v1/customer/auth/login", null, device, Map.of(
                "institutionCode", tenant.code(), "phoneNumber", nationalPhone, "password", password));
    }

    private static Map<String, Object> completion(String token, String code, String pin) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("challengeToken", token);
        request.put("code", code);
        request.put("password", PASSWORD);
        request.put("pin", pin);
        request.put("deviceName", "Samsung A14");
        request.put("platform", "android");
        return request;
    }

    private String lastMessage() {
        return sms.lastTo(phone).orElseThrow().body();
    }

    private String lastCode() {
        Matcher matcher = CODE.matcher(lastMessage());
        assertThat(matcher.find()).as("the last text carries a code: %s", lastMessage()).isTrue();
        return matcher.group(1);
    }
}
