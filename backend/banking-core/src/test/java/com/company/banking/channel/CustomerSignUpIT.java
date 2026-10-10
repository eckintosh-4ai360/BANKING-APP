package com.company.banking.channel;

import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.common.outbox.OutboxService;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.notification.service.StubSmsGateway;
import com.company.banking.support.CustomerApp;
import com.company.banking.support.Fixtures;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Someone who is not yet a customer signs up in the app: verifies their phone, registers, captures their details and
 * documents stage by stage, submits them for review by staff and, once approved, opens their first account.
 */
class CustomerSignUpIT extends IntegrationTest {

    private static final String ONBOARDING = "/api/v1/customer/onboarding";

    @Autowired
    private StubSmsGateway sms;

    @Autowired
    private OutboxService outbox;

    private CustomerApp app;
    private TenantHandle tenant;
    private StaffHandle manager;
    private String productId;

    @BeforeEach
    void setUp() {
        app = new CustomerApp(api, sms);
        tenant = fixtures.onboardTenant();
        fixtures.enableFeature(tenant, "CUSTOMER_MOBILE_APP");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        Map<String, Object> savings = terms("0");
        savings.put("interestRate", "0");
        productId = fixtures.publishedProduct(tenant, "SAV01", "SAVINGS", savings);
    }

    @Test
    void aNewCustomerSignsUpIsReviewedByStaffAndOpensTheirFirstAccount() {
        String phone = phone();
        String device = "phone-" + UUID.randomUUID();
        api.fromDevice("POST", "/api/v1/customer/auth/sign-up", null, device, Map.of(
                        "institutionCode", tenant.code(), "phoneNumber", phone))
                .expectError(403, "SIGN_UP_NOT_AVAILABLE");
        JsonNode settings = enableSignUp();
        assertThat(settings.at("/onboarding/enabled").asBoolean()).isTrue();
        assertThat(settings.at("/onboarding/tierCode").asString()).isEqualTo("TIER_2");

        // Registering: the phone is verified, a pending customer exists and is signed in.
        CustomerApp.Session esi = app.signUp(tenant, phone, device, "Esi", "Boateng", "1995-04-12");
        JsonNode progress = app.get(esi, ONBOARDING).expect(200).data();
        assertThat(progress.get("status").asString()).isEqualTo("PENDING");
        assertThat(state(progress, "PHONE")).isEqualTo("DONE");
        assertThat(state(progress, "REGISTRATION")).isEqualTo("DONE");
        assertThat(progress.get("nextStage").asString()).isEqualTo("PERSONAL_DETAILS");
        assertThat(stage(progress, "SIGNATURE").get("required").asBoolean()).as("TIER_2 needs no signature")
                .isFalse();
        assertThat(stage(progress, "SELFIE").get("required").asBoolean()).isTrue();
        assertThat(app.get(esi, "/api/v1/customer/accounts").expect(200).data().get("accounts")).isEmpty();
        app.send(esi, "POST", ONBOARDING + "/submit", null).expectError(422, "SIGN_UP_INCOMPLETE");

        // Capturing, stage by stage.
        app.send(esi, "PUT", ONBOARDING + "/personal", Map.of("firstName", "Esi", "lastName", "Boateng",
                "dateOfBirth", "1995-04-12", "gender", "FEMALE", "nationality", "GH", "email", "esi@example.com"))
                .expect(200);
        app.send(esi, "PUT", ONBOARDING + "/employment", Map.of("employmentStatus", "SELF_EMPLOYED",
                "occupation", "Trader")).expect(200);
        assertThat(app.get(esi, ONBOARDING + "/id-types").expect(200).data())
                .extracting(type -> type.get("code").asString()).contains("GHANA_CARD");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        // The stub identity provider fails numbers ending in 9.
        String idNumber = "GHA-" + (100_000_000 + random.nextInt(899_999_999)) + "-" + random.nextInt(9);
        app.send(esi, "PUT", ONBOARDING + "/identification", Map.of("idTypeCode", "GHANA_CARD",
                "idNumber", idNumber, "issuingCountry", "GH", "expiryDate", "2031-01-01")).expect(200);
        app.upload(esi, ONBOARDING + "/documents", "card.png", Fixtures.PNG, "documentType", "ID_FRONT")
                .expect(200);
        app.upload(esi, ONBOARDING + "/documents", "me.jpg", Fixtures.JPEG, "documentType", "SELFIE").expect(200);
        app.send(esi, "PUT", ONBOARDING + "/address", Map.of("line1", "4 Palm Lane", "city", "Kumasi",
                "digitalAddress", "AK-039-5028")).expect(200);
        app.send(esi, "PUT", ONBOARDING + "/next-of-kin", Map.of("fullName", "Kwame Boateng",
                "relationship", "Brother", "phone", "+233244000222")).expect(200);
        progress = app.send(esi, "PUT", ONBOARDING + "/risk-profile", Map.of("sourceOfFunds", "TRADING",
                "accountPurpose", "SAVINGS", "expectedMonthlyTurnover", "UP_TO_5000", "politicallyExposed", true))
                .expect(200).data();
        assertThat(progress.get("nextStage").asString()).isEqualTo("COMPLIANCE");
        assertThat(progress.at("/profile/occupation").asString()).isEqualTo("Trader");
        assertThat(progress.at("/identification/idNumberMasked").asString()).isNotEqualTo(idNumber).contains("*");

        // Submitting: verified electronically where possible, then in review; nothing changes meanwhile.
        progress = app.send(esi, "POST", ONBOARDING + "/submit", null).expect(200).data();
        assertThat(state(progress, "COMPLIANCE")).isEqualTo("IN_REVIEW");
        assertThat(progress.get("nextStage").asString()).isEqualTo("COMPLIANCE");
        app.send(esi, "PUT", ONBOARDING + "/address", Map.of("line1", "5 Palm Lane")).expectError(422,
                "KYC_UNDER_REVIEW");
        app.send(esi, "POST", ONBOARDING + "/submit", null).expect(200);

        // Staff review the case like any other: the customer submitted it, the declared PEP is a screening match.
        JsonNode summary = api.get("/api/v1/kyc/cases?status=PENDING_REVIEW", manager.token()).expect(200).data()
                .at("/items/0");
        String customerId = summary.get("customerId").asString();
        String caseId = summary.get("id").asString();
        JsonNode kycCase = api.get("/api/v1/kyc/cases/" + caseId, manager.token()).expect(200).data();
        assertThat(kycCase.get("submittedBy").asString()).isEqualTo(customerId);
        assertThat(kycCase.get("checks")).extracting(check -> check.get("checkType").asString() + " "
                + check.get("method").asString() + " " + check.get("result").asString())
                .contains("IDENTITY_VERIFICATION ELECTRONIC PASS", "PEP DECLARED FAIL");
        JsonNode signUp = api.get("/api/v1/customers/" + customerId + "/sign-up", manager.token()).expect(200)
                .data();
        assertThat(signUp.get("politicallyExposed").asBoolean()).isTrue();
        assertThat(signUp.get("sourceOfFunds").asString()).isEqualTo("TRADING");
        JsonNode customer = api.get("/api/v1/customers/" + customerId, manager.token()).expect(200).data();
        assertThat(customer.get("onboardingChannel").asString()).isEqualTo("MOBILE");
        assertThat(customer.get("homeBranchId").asString()).isEqualTo(tenant.headOfficeId().toString());

        // Returned for correction: the customer sees what to correct, corrects it and submits again.
        api.post("/api/v1/kyc/cases/" + caseId + "/return", manager.token(), Map.of(
                "note", "Please give your full street address", "version", kycCase.get("version").asLong()))
                .expect(200);
        relay();
        progress = app.get(esi, ONBOARDING).expect(200).data();
        assertThat(state(progress, "COMPLIANCE")).isEqualTo("ACTION_NEEDED");
        assertThat(progress.at("/review/note").asString()).isEqualTo("Please give your full street address");
        assertThat(latestNotification(esi).get("title").asString()).isEqualTo("Details to correct");
        assertThat(app.lastMessage(phone)).contains("some of your details need correcting")
                .doesNotContain("street address");
        app.send(esi, "PUT", ONBOARDING + "/address", Map.of("line1", "4 Palm Lane, Asokwa", "city", "Kumasi",
                "digitalAddress", "AK-039-5028")).expect(200);
        assertThat(app.send(esi, "POST", ONBOARDING + "/submit", null).expect(200).data().at("/review/status")
                .asString()).isEqualTo("PENDING_REVIEW");

        // Approved: the documents accepted, and a high risk rating for the declared PEP.
        for (JsonNode document : api.get("/api/v1/customers/" + customerId + "/documents", manager.token())
                .expect(200).data()) {
            api.post("/api/v1/customers/" + customerId + "/documents/" + document.get("id").asString() + "/review",
                    manager.token(), Map.of("decision", "ACCEPTED")).expect(200);
        }
        long version = api.get("/api/v1/kyc/cases/" + caseId, manager.token()).expect(200).data().get("version")
                .asLong();
        api.post("/api/v1/kyc/cases/" + caseId + "/approve", manager.token(), Map.of("riskLevel", "LOW",
                "note", "Checked", "version", version)).expectError(422, "HIGH_RISK_REQUIRED");
        api.post("/api/v1/kyc/cases/" + caseId + "/approve", manager.token(), Map.of("riskLevel", "HIGH",
                "note", "Checked; declared PEP", "version", version)).expect(200);
        relay();
        assertThat(latestNotification(esi).get("title").asString()).isEqualTo("You are approved");
        progress = app.get(esi, ONBOARDING).expect(200).data();
        assertThat(progress.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(progress.get("nextStage").asString()).isEqualTo("ACTIVATION");

        // The first account, once.
        JsonNode opened = app.send(esi, "POST", ONBOARDING + "/account", null).expect(200).data();
        assertThat(opened.hasNonNull("nextStage")).isFalse();
        assertThat(state(opened, "ACTIVATION")).isEqualTo("DONE");
        String accountNumber = opened.at("/account/accountNumber").asString();
        assertThat(opened.at("/account/productName").asString()).isNotBlank();
        assertThat(app.send(esi, "POST", ONBOARDING + "/account", null).expect(200).data()
                .at("/account/accountNumber").asString()).isEqualTo(accountNumber);
        assertThat(app.get(esi, "/api/v1/customer/accounts").expect(200).data().get("accounts"))
                .extracting(account -> account.get("accountNumber").asString()).containsExactly(accountNumber);
        assertThat(latestNotification(esi).get("title").asString()).isEqualTo("Your account is open");
        app.send(esi, "PUT", ONBOARDING + "/personal", Map.of("firstName", "Esi", "lastName", "Boateng",
                "dateOfBirth", "1995-04-12", "gender", "FEMALE", "nationality", "GH")).expectError(409,
                "NOT_SIGNING_UP");
    }

    @Test
    void signUpIsRefusedToTheTooYoungAndToNumbersAlreadyUsingTheApp() {
        // Turning it on needs a branch, a tier and a savings or current product in the base currency.
        long version = api.get("/api/v1/channel-settings", tenant.adminToken()).expect(200).data().get("version")
                .asLong();
        api.put("/api/v1/channel-settings/onboarding", tenant.adminToken(), Map.of("enabled", true,
                "branchId", tenant.headOfficeId().toString(), "tierCode", "TIER_2", "minimumAge", 18,
                "version", version)).expectError(422, "BUSINESS_RULE_VIOLATION");
        Map<String, Object> susuTerms = terms("0");
        susuTerms.put("interestRate", "0");
        String susu = fixtures.publishedProduct(tenant, "SUSU1", "SUSU", susuTerms);
        api.put("/api/v1/channel-settings/onboarding", tenant.adminToken(), Map.of("enabled", true,
                "branchId", tenant.headOfficeId().toString(), "tierCode", "TIER_2", "productId", susu,
                "minimumAge", 18, "version", version)).expectError(422, "BUSINESS_RULE_VIOLATION");
        enableSignUp();
        String phone = phone();
        String device = "phone-" + UUID.randomUUID();
        String challenge = app.startSignUp(tenant, phone, device);
        String child = LocalDate.now().minusYears(12).toString();
        app.completeSignUp(challenge, phone, device, "Ama", "Owusu", child).expectError(422, "TOO_YOUNG");
        // The code was not used up by the refusal.
        CustomerApp.Session ama = new CustomerApp.Session(app.completeSignUp(challenge, phone, device, "Ama",
                "Owusu", "2001-02-03").expect(200).data().get("accessToken").asString(), device, phone);
        app.get(ama, ONBOARDING).expect(200);

        // The number now has mobile banking: no code, a reminder instead (the answer looks the same).
        String again = app.startSignUp(tenant, phone, "other-" + UUID.randomUUID());
        assertThat(again).isNotBlank();
        assertThat(app.lastMessage(phone)).contains("already set up").doesNotContain("your code");

        // An existing customer has nothing to sign up for.
        StaffHandle lender = fixtures.createStaff(tenant, "lender", tenant.headOfficeId(), false, "LOAN_OFFICER");
        UUID kofiId = fixtures.verifiedIndividual(lender, manager, tenant.headOfficeId(), "Kofi");
        JsonNode kofi = api.get("/api/v1/customers/" + kofiId, manager.token()).expect(200).data();
        CustomerApp.Session session = app.activate(tenant, kofi.get("customerNumber").asString(),
                kofi.get("primaryPhone").asString(), "phone-" + UUID.randomUUID());
        app.get(session, ONBOARDING).expectError(409, "NOT_SIGNING_UP");
        app.send(session, "POST", ONBOARDING + "/account", null).expectError(409, "NOT_SIGNING_UP");
    }

    private JsonNode enableSignUp() {
        long version = api.get("/api/v1/channel-settings", tenant.adminToken()).expect(200).data().get("version")
                .asLong();
        Map<String, Object> onboarding = new HashMap<>();
        onboarding.put("enabled", true);
        onboarding.put("branchId", tenant.headOfficeId().toString());
        onboarding.put("tierCode", "TIER_2");
        onboarding.put("productId", productId);
        onboarding.put("minimumAge", 18);
        onboarding.put("version", version);
        return api.put("/api/v1/channel-settings/onboarding", tenant.adminToken(), onboarding).expect(200).data();
    }

    private JsonNode latestNotification(CustomerApp.Session session) {
        return app.get(session, "/api/v1/customer/notifications").expect(200).data().at("/items/0");
    }

    private void relay() {
        TenantContext.callAs(tenant.id(), () -> CurrentActor.callAsSystem(tenant.id(),
                () -> outbox.relayPending(100)));
    }

    private static String phone() {
        return "+23320" + (1_000_000 + ThreadLocalRandom.current().nextInt(8_999_999));
    }

    private static JsonNode stage(JsonNode progress, String code) {
        for (JsonNode stage : progress.get("stages")) {
            if (stage.get("code").asString().equals(code)) {
                return stage;
            }
        }
        throw new AssertionError("No stage " + code);
    }

    private static String state(JsonNode progress, String code) {
        return stage(progress, code).get("state").asString();
    }
}
