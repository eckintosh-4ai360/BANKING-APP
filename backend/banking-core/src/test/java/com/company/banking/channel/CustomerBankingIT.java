package com.company.banking.channel;

import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.notification.service.StubSmsGateway;
import com.company.banking.support.CustomerApp;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Banking in the customer app: own accounts and statements, transfers confirmed with the PIN (idempotent), the
 * institution's limits (per transfer, per day, and tighter for new beneficiaries, typed-in accounts and new devices),
 * and beneficiaries. Transfers between the customer's own accounts are limited only by their products.
 */
class CustomerBankingIT extends IntegrationTest {

    @Autowired
    private StubSmsGateway sms;

    private CustomerApp app;
    private TenantHandle tenant;
    private StaffHandle manager;
    private CustomerApp.Session abena;
    private String mainAccount;
    private String secondAccount;
    private String secondAccountNumber;
    private String kofiAccount;
    private String kofiAccountNumber;

    @BeforeEach
    void setUp() {
        app = new CustomerApp(api, sms);
        tenant = fixtures.onboardTenant();
        fixtures.enableFeature(tenant, "CUSTOMER_MOBILE_APP");
        StaffHandle lender = fixtures.createStaff(tenant, "lender", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        StaffHandle teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        Map<String, Object> savings = terms("0");
        savings.put("interestRate", "0");
        String product = fixtures.publishedProduct(tenant, "SAV01", "SAVINGS", savings);

        UUID abenaId = fixtures.verifiedIndividual(lender, manager, tenant.headOfficeId(), "Abena");
        mainAccount = fixtures.openAccount(manager.token(), abenaId, product).get("id").asString();
        JsonNode second = fixtures.openAccount(manager.token(), abenaId, product);
        secondAccount = second.get("id").asString();
        secondAccountNumber = second.get("accountNumber").asString();
        UUID kofiId = fixtures.verifiedIndividual(lender, manager, tenant.headOfficeId(), "Kofi");
        JsonNode kofi = fixtures.openAccount(manager.token(), kofiId, product);
        kofiAccount = kofi.get("id").asString();
        kofiAccountNumber = kofi.get("accountNumber").asString();

        fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");
        api.postIdempotent("/api/v1/transactions/deposits", teller.token(), "deposit-" + UUID.randomUUID(),
                Map.of("accountId", mainAccount, "amount", "3000.00")).expect(201);

        JsonNode customer = api.get("/api/v1/customers/" + abenaId, manager.token()).expect(200).data();
        abena = app.activate(tenant, customer.get("customerNumber").asString(),
                customer.get("primaryPhone").asString(), "phone-" + UUID.randomUUID());
    }

    @Test
    void ownAccountsStatementsAndTransfersBetweenThem() {
        JsonNode accounts = app.get(abena, "/api/v1/customer/accounts").expect(200).data();
        assertThat(accounts.get("accounts")).hasSize(2);
        assertThat(accounts.at("/totals/0/currency").asString()).isEqualTo("GHS");
        assertThat(accounts.at("/totals/0/available").asString()).isEqualTo("3000.00");

        JsonNode statement = app.get(abena, "/api/v1/customer/accounts/" + mainAccount + "/statement").expect(200)
                .data();
        assertThat(statement.get("closingBalance").asString()).isEqualTo("3000.00");
        app.get(abena, "/api/v1/customer/accounts/" + kofiAccount + "/statement").expectError(404, "RESOURCE_NOT_FOUND");

        // Above every channel limit, but between her own accounts: only the product rules apply.
        Map<String, Object> own = transfer(secondAccountNumber, null, "1200.00", CustomerApp.PIN);
        JsonNode receipt = app.pay(abena, "/api/v1/customer/transfers", "transfer-own-1", own).expect(201).data();
        assertThat(receipt.get("availableAfter").asString()).isEqualTo("1800.00");
        assertThat(receipt.get("toAccountNumber").asString()).isEqualTo(secondAccountNumber);
        JsonNode replay = app.pay(abena, "/api/v1/customer/transfers", "transfer-own-1", own).expect(201).data();
        assertThat(replay.get("transactionId").asString()).isEqualTo(receipt.get("transactionId").asString());
        assertThat(balance(mainAccount)).as("moved once").isEqualTo("1800.00");

        app.pay(abena, "/api/v1/customer/transfers", "transfer-own-2", transfer(secondAccountNumber, null, "10.00", "9753"))
                .expectError(422, "WRONG_PIN");
        assertThat(balance(mainAccount)).isEqualTo("1800.00");
        assertThat(balance(secondAccount)).isEqualTo("1200.00");
        assertThat(app.get(abena, "/api/v1/customer/susu-plans").expect(200).data()).isEmpty();
        assertThat(app.get(abena, "/api/v1/customer/loans").expect(200).data()).isEmpty();
    }

    @Test
    void transfersToOthersAreLimitedWhileTheDestinationOrDeviceIsNewAndPerDay() {
        JsonNode destination = app.send(abena, "POST", "/api/v1/customer/transfers/destination", Map.of(
                "accountNumber", kofiAccountNumber)).expect(200).data();
        assertThat(destination.get("name").asString()).isEqualTo("Kofi M.");
        app.send(abena, "POST", "/api/v1/customer/transfers/destination", Map.of("accountNumber", "1999999999"))
                .expectError(422, "DESTINATION_NOT_FOUND");

        // A typed-in account from a device trusted minutes ago: at most 1,000.
        app.pay(abena, "/api/v1/customer/transfers", "transfer-1", transfer(kofiAccountNumber, null, "1100.00",
                CustomerApp.PIN)).expectError(422, "TRANSFER_LIMIT");
        app.pay(abena, "/api/v1/customer/transfers", "transfer-2", transfer(kofiAccountNumber, null, "500.00",
                CustomerApp.PIN)).expect(201);
        assertThat(app.lastMessage(abena.phone())).contains("you sent GHS 500.00 from the app");

        String beneficiaries = "/api/v1/customer/beneficiaries";
        app.send(abena, "POST", beneficiaries, beneficiary("BANK", kofiAccountNumber, CustomerApp.PIN))
                .expectError(422, "BENEFICIARY_TYPE_NOT_AVAILABLE");
        app.send(abena, "POST", beneficiaries, beneficiary("INTERNAL", secondAccountNumber, CustomerApp.PIN))
                .expectError(422, "OWN_ACCOUNT");
        app.send(abena, "POST", beneficiaries, beneficiary("INTERNAL", kofiAccountNumber, "9753"))
                .expectError(422, "WRONG_PIN");
        JsonNode saved = app.send(abena, "POST", beneficiaries, beneficiary("INTERNAL", kofiAccountNumber,
                CustomerApp.PIN)).expect(201).data();
        assertThat(saved.get("name").asString()).isEqualTo("Kofi M.");
        assertThat(saved.get("coolingDownUntil").isNull()).isFalse();
        assertThat(app.lastMessage(abena.phone())).contains("a new beneficiary (Kofi) was added");
        app.send(abena, "POST", beneficiaries, beneficiary("INTERNAL", kofiAccountNumber, CustomerApp.PIN))
                .expectError(409, "BENEFICIARY_EXISTS");
        app.pay(abena, "/api/v1/customer/transfers", "transfer-3", transfer(null, saved.get("id").asString(), "1100.00",
                CustomerApp.PIN)).expectError(422, "TRANSFER_LIMIT");

        // The institution drops the cooldowns and sets a daily limit of 2,000.
        JsonNode settings = api.get("/api/v1/channel-settings", tenant.adminToken()).expect(200).data();
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("maxTransferAmount", "2000.00");
        update.put("dailyTransferLimit", "2000.00");
        update.put("beneficiaryCooldownHours", 0);
        update.put("cooldownMaxAmount", "1000.00");
        update.put("newDeviceCooldownHours", 0);
        update.put("newDeviceMaxAmount", "1000.00");
        update.put("version", settings.get("version").asLong());
        api.put("/api/v1/channel-settings", manager.token(), update).expectError(403, "ACCESS_DENIED");
        api.put("/api/v1/channel-settings", tenant.adminToken(), update).expect(200);

        // An existing beneficiary keeps the cooldown it was saved with; saved again, it has none.
        app.send(abena, "DELETE", beneficiaries + "/" + saved.get("id").asString(), null).expect(200);
        JsonNode resaved = app.send(abena, "POST", beneficiaries, beneficiary("INTERNAL", kofiAccountNumber,
                CustomerApp.PIN)).expect(201).data();
        assertThat(resaved.get("coolingDownUntil").isNull()).isTrue();
        String kofi = resaved.get("id").asString();
        app.pay(abena, "/api/v1/customer/transfers", "transfer-4", transfer(null, kofi, "1400.00", CustomerApp.PIN))
                .expect(201);
        JsonNode overDaily = app.pay(abena, "/api/v1/customer/transfers", "transfer-5", transfer(null, kofi, "200.00",
                CustomerApp.PIN)).expectError(422, "TRANSFER_LIMIT").body();
        assertThat(overDaily.get("message").asString()).contains("daily limit");
        assertThat(balance(kofiAccount)).isEqualTo("1900.00");

        // A beneficiary's own limit: lowering needs nothing, raising needs the PIN.
        JsonNode limited = app.send(abena, "PUT", beneficiaries + "/" + kofi, Map.of("nickname", "Kofi",
                "favourite", true, "transferLimit", "100.00", "version", resaved.get("version").asLong()))
                .expect(200).data();
        JsonNode overLimit = app.pay(abena, "/api/v1/customer/transfers", "transfer-6", transfer(null, kofi, "150.00",
                CustomerApp.PIN)).expectError(422, "TRANSFER_LIMIT").body();
        assertThat(overLimit.get("message").asString()).contains("the limit you set for this beneficiary");
        app.send(abena, "PUT", beneficiaries + "/" + kofi, Map.of("nickname", "Kofi", "favourite", true,
                "transferLimit", "1000.00", "version", limited.get("version").asLong())).expectError(422, "WRONG_PIN");
        app.send(abena, "PUT", beneficiaries + "/" + kofi, Map.of("nickname", "Kofi", "favourite", true,
                "transferLimit", "1000.00", "pin", CustomerApp.PIN, "version", limited.get("version").asLong()))
                .expect(200);
        assertThat(app.get(abena, beneficiaries).expect(200).data()).hasSize(1);
    }

    // ---------------------------------------------------------------------------------------------------------

    private Map<String, Object> transfer(String toAccountNumber, String beneficiaryId, String amount, String pin) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("fromAccountId", mainAccount);
        if (toAccountNumber != null) {
            request.put("toAccountNumber", toAccountNumber);
        }
        if (beneficiaryId != null) {
            request.put("beneficiaryId", beneficiaryId);
        }
        request.put("amount", amount);
        request.put("pin", pin);
        return request;
    }

    private static Map<String, Object> beneficiary(String type, String accountNumber, String pin) {
        return Map.of("type", type, "accountNumber", accountNumber, "nickname", "Kofi", "favourite", false,
                "pin", pin);
    }

    private String balance(String accountId) {
        return api.get("/api/v1/accounts/" + accountId, manager.token()).expect(200).data().get("ledgerBalance")
                .asString();
    }
}
