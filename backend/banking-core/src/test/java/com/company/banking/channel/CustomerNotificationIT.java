package com.company.banking.channel;

import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.common.outbox.OutboxService;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.notification.service.StubPushGateway;
import com.company.banking.notification.service.StubSmsGateway;
import com.company.banking.support.CustomerApp;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Alerts for money in and out reach customers who use the app: in the inbox, pushed to their devices and texted
 * when they chose text alerts. They are made from the outbox after the posting committed.
 */
class CustomerNotificationIT extends IntegrationTest {

    @Autowired
    private StubSmsGateway sms;

    @Autowired
    private StubPushGateway push;

    @Autowired
    private OutboxService outbox;

    private CustomerApp app;
    private TenantHandle tenant;
    private StaffHandle manager;
    private StaffHandle teller;
    private String accountId;
    private String accountNumber;
    private CustomerApp.Session abena;

    @BeforeEach
    void setUp() {
        app = new CustomerApp(api, sms);
        tenant = fixtures.onboardTenant();
        fixtures.enableFeature(tenant, "CUSTOMER_MOBILE_APP");
        StaffHandle lender = fixtures.createStaff(tenant, "lender", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        Map<String, Object> savings = terms("0");
        savings.put("interestRate", "0");
        UUID customerId = fixtures.verifiedIndividual(lender, manager, tenant.headOfficeId(), "Abena");
        JsonNode account = fixtures.openAccount(manager.token(), customerId,
                fixtures.publishedProduct(tenant, "SAV01", "SAVINGS", savings));
        accountId = account.get("id").asString();
        accountNumber = account.get("accountNumber").asString();
        fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");
        JsonNode customer = api.get("/api/v1/customers/" + customerId, manager.token()).expect(200).data();
        abena = app.activate(tenant, customer.get("customerNumber").asString(),
                customer.get("primaryPhone").asString(), "phone-" + UUID.randomUUID());
    }

    @Test
    void moneyInAndOutIsAlertedInTheInboxByPushAndByTextWhenChosen() {
        String token = "fcm-" + UUID.randomUUID();
        app.send(abena, "PUT", "/api/v1/customer/notifications/push", Map.of("provider", "FCM", "token", token))
                .expect(200);

        // Setting up the app left a security notice (also texted).
        JsonNode welcome = app.get(abena, "/api/v1/customer/notifications").expect(200).data().at("/items/0");
        assertThat(welcome.get("category").asString()).isEqualTo("SECURITY");
        assertThat(welcome.get("title").asString()).isEqualTo("Welcome");

        cash("deposits", "500.00");
        relay();
        JsonNode inbox = app.get(abena, "/api/v1/customer/notifications").expect(200).data();
        assertThat(inbox.get("items")).hasSize(2);
        JsonNode deposit = inbox.at("/items/0");
        assertThat(deposit.get("category").asString()).isEqualTo("TRANSACTION");
        String last4 = accountNumber.substring(accountNumber.length() - 4);
        assertThat(deposit.get("title").asString()).isEqualTo("Deposit received");
        assertThat(deposit.get("body").asString()).startsWith("GHS 500.00 was paid into your account ending " + last4);
        assertThat(deposit.get("read").asBoolean()).isFalse();
        assertThat(app.lastMessage(abena.phone())).contains("GHS 500.00 was paid into your account ending " + last4);
        assertThat(push.sentTo(token)).hasSize(1);
        assertThat(push.sentTo(token).getFirst().title()).isEqualTo("Deposit received");
        relay();
        assertThat(app.get(abena, "/api/v1/customer/notifications").expect(200).data().get("items"))
                .as("delivered once").hasSize(2);

        // Text alerts off: the inbox and push still get it, no text.
        app.send(abena, "PUT", "/api/v1/customer/notifications/preferences", Map.of("smsAlerts", false))
                .expect(200);
        String lastText = app.lastMessage(abena.phone());
        cash("withdrawals", "120.00");
        relay();
        assertThat(app.lastMessage(abena.phone())).isEqualTo(lastText);
        JsonNode withdrawal = app.get(abena, "/api/v1/customer/notifications").expect(200).data().at("/items/0");
        assertThat(withdrawal.get("title").asString()).isEqualTo("Cash withdrawn");
        assertThat(withdrawal.get("body").asString()).startsWith("GHS 120.00 was taken from your account ending");
        assertThat(push.sentTo(token)).hasSize(2);

        assertThat(app.get(abena, "/api/v1/customer/notifications/unread").expect(200).data().get("count").asLong())
                .isEqualTo(3);
        app.send(abena, "POST", "/api/v1/customer/notifications/" + withdrawal.get("id").asString() + "/read", null)
                .expect(200);
        assertThat(app.get(abena, "/api/v1/customer/notifications/unread").expect(200).data().get("count").asLong())
                .isEqualTo(2);
        app.send(abena, "POST", "/api/v1/customer/notifications/read", null).expect(200);
        assertThat(app.get(abena, "/api/v1/customer/notifications/unread").expect(200).data().get("count").asLong())
                .isZero();

        app.send(abena, "DELETE", "/api/v1/customer/notifications/push", null).expect(200);
        cash("deposits", "10.00");
        relay();
        assertThat(push.sentTo(token)).as("no more pushes").hasSize(2);
    }

    @Test
    void aReversalIsAlertedWithItsAmountAndAccount() {
        JsonNode deposit = cash("deposits", "300.00");
        StaffHandle supervisor = fixtures.createStaff(tenant, "supervisor", tenant.headOfficeId(), false,
                "BRANCH_MANAGER");
        JsonNode approval = api.post("/api/v1/transactions/" + deposit.get("id").asString() + "/reversal",
                manager.token(), Map.of("reason", "Paid to the wrong customer")).expect(202).data();
        api.post("/api/v1/approvals/" + approval.get("id").asString() + "/approve", supervisor.token(),
                Map.of("note", "Checked", "version", approval.get("version").asLong())).expect(200);
        relay();

        JsonNode items = app.get(abena, "/api/v1/customer/notifications").expect(200).data().get("items");
        assertThat(items).extracting(item -> item.get("title").asString())
                .containsExactly("Transaction reversed", "Deposit received", "Welcome");
        assertThat(items.at("/0/body").asString()).isEqualTo("Transaction " + deposit.get("reference").asString()
                + " of GHS 300.00 on your account ending " + accountNumber.substring(accountNumber.length() - 4)
                + " was reversed.");
        assertThat(app.lastMessage(abena.phone())).contains("of GHS 300.00 on your account ending");
    }

    private JsonNode cash(String kind, String amount) {
        return api.postIdempotent("/api/v1/transactions/" + kind, teller.token(), "cash-" + UUID.randomUUID(),
                Map.of("accountId", accountId, "amount", amount)).expect(201).data().get("transaction");
    }

    private void relay() {
        TenantContext.callAs(tenant.id(), () -> CurrentActor.callAsSystem(tenant.id(),
                () -> outbox.relayPending(100)));
    }
}
