package com.company.banking.fieldops;

import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.support.Api;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * Field collections from registration to remittance. The gate: replaying the same offline batch three times posts
 * once, device sequence gaps raise alerts (and close when the collections arrive), and an officer's cash position
 * reconciles with the ledger.
 */
class FieldOperationsIT extends LedgerIntegrationTest {

    private static final String SYNC = "/api/v1/field/sync";

    @Autowired
    private JdbcClient jdbcClient;

    private TenantHandle tenant;
    private StaffHandle lender;
    private StaffHandle manager;
    private StaffHandle officer;
    private StaffHandle teller;
    private String customerId;
    private String accountId;
    private String deviceId;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        lender = fixtures.createStaff(tenant, "lender", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        officer = fixtures.createStaff(tenant, "collector", tenant.headOfficeId(), false, "FIELD_OFFICER");
        teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        customerId = fixtures.verifiedIndividual(lender, manager, tenant.headOfficeId(), "Abena").toString();
        accountId = fixtures.openAccount(manager.token(), UUID.fromString(customerId),
                fixtures.publishedProduct(tenant, "SAV01", "SAVINGS", terms("0"))).get("id").asString();

        registerOfficer("1000.00", 24);
        api.post("/api/v1/field/assignments", manager.token(), Map.of("customerId", customerId,
                "officerId", officer.id().toString())).expect(201);
        deviceId = api.post("/api/v1/field/devices", officer.token(), Map.of("deviceKey", "phone-" + officer.id(),
                "name", "Tecno Spark")).expect(200).data().get("id").asString();
    }

    @Test
    void replayingTheSameOfflineBatchPostsOnce() {
        JsonNode customers = api.get("/api/v1/field/me/customers", officer.token()).expect(200).data();
        assertThat(customers).singleElement().satisfies(customer -> {
            assertThat(customer.get("customerId").asString()).isEqualTo(customerId);
            assertThat(customer.at("/accounts/0/accountId").asString()).isEqualTo(accountId);
        });

        List<Map<String, Object>> batch = List.of(collection(1, "50.00"), collection(2, "20.50"),
                collection(3, "9.50"));
        JsonNode first = sync(batch);
        assertThat(statuses(first)).containsExactly("POSTED", "POSTED", "POSTED");
        assertThat(first.at("/collections/2/balanceAfter").asString()).isEqualTo("80.00");
        assertThat(first.at("/collections/0/transactionReference").asString()).startsWith("TXN-");
        assertThat(first.get("highestSequenceNo").asLong()).isEqualTo(3);
        assertThat(first.get("missing")).isEmpty();

        for (int replay = 0; replay < 3; replay++) {
            JsonNode again = sync(batch);
            assertThat(statuses(again)).containsExactly("DUPLICATE", "DUPLICATE", "DUPLICATE");
            assertThat(again.at("/collections/0/originalStatus").asString()).isEqualTo("POSTED");
            assertThat(again.at("/collections/0/transactionReference").asString())
                    .isEqualTo(first.at("/collections/0/transactionReference").asString());
        }
        assertThat(balance(accountId)).isEqualTo("80.00");
        assertThat(count("SELECT count(*) FROM core.financial_transaction WHERE transaction_type = 'FIELD_COLLECTION'"))
                .isEqualTo(3);
        assertThat(count("SELECT count(*) FROM core.collection")).isEqualTo(3);

        JsonNode position = cashPosition();
        assertThat(position.get("ledgerBalance").asString()).isEqualTo("80.00");
        assertThat(position.get("collected").asString()).isEqualTo("80.00");
        assertThat(position.get("reconciled").asBoolean()).isTrue();
        assertThat(api.get("/api/v1/accounts/" + accountId + "/transactions", manager.token()).expect(200).data()
                .at("/items/0/channel").asString()).isEqualTo("FIELD");
    }

    @Test
    void aDifferentCollectionUnderAUsedReferenceOrNumberIsAConflict() {
        Map<String, Object> original = collection(1, "40.00");
        sync(List.of(original));

        Map<String, Object> changed = new LinkedHashMap<>(original);
        changed.put("amount", "400.00");
        Map<String, Object> reusedNumber = collection(1, "15.00");
        JsonNode result = sync(List.of(changed, reusedNumber));

        assertThat(statuses(result)).containsExactly("CONFLICT", "CONFLICT");
        assertThat(balance(accountId)).isEqualTo("40.00");
        JsonNode alerts = alerts("OPEN");
        assertThat(alerts.get("items")).hasSize(2)
                .allSatisfy(alert -> assertThat(alert.get("alertType").asString()).isEqualTo("CONFLICT"));
    }

    @Test
    void aSequenceGapRaisesAnAlertThatClosesWhenTheCollectionsArrive() {
        Map<String, Object> fourth = collection(4, "10.00");
        Map<String, Object> fifth = collection(5, "10.00");
        sync(List.of(collection(1, "10.00"), collection(2, "10.00")));

        JsonNode withGap = sync(List.of(collection(6, "10.00")));
        assertThat(withGap.get("missing")).singleElement().satisfies(range -> {
            assertThat(range.get("from").asLong()).isEqualTo(3);
            assertThat(range.get("to").asLong()).isEqualTo(5);
        });
        JsonNode open = alerts("OPEN").get("items");
        assertThat(open).singleElement().satisfies(alert -> {
            assertThat(alert.get("alertType").asString()).isEqualTo("SEQUENCE_GAP");
            assertThat(alert.get("detail").asString()).isEqualTo("Collections 3 to 5 of the device never arrived");
        });

        JsonNode partly = sync(List.of(fourth, fifth));
        assertThat(partly.get("missing")).singleElement().satisfies(range ->
                assertThat(range.get("to").asLong()).isEqualTo(3));
        assertThat(alerts("OPEN").get("items")).singleElement().satisfies(alert ->
                assertThat(alert.get("detail").asString()).isEqualTo("Collection 3 of the device never arrived"));

        assertThat(sync(List.of(collection(3, "10.00"))).get("missing")).isEmpty();
        assertThat(alerts("OPEN").get("items")).isEmpty();
        assertThat(alerts("RESOLVED").get("items")).hasSize(2);
        assertThat(balance(accountId)).isEqualTo("60.00");
    }

    @Test
    void refusedCollectionsAreRecordedAndTheCashStaysWithTheOfficer() {
        String stranger = fixtures.verifiedIndividual(lender, manager, tenant.headOfficeId(), "Kofi").toString();
        Map<String, Object> notMine = collection(1, "25.00");
        notMine.put("customerId", stranger);
        Map<String, Object> tooPrecise = collection(2, "1.005");
        Map<String, Object> future = collection(3, "5.00");
        future.put("collectedAt", Instant.now().plus(2, ChronoUnit.HOURS).toString());

        JsonNode result = sync(List.of(notMine, tooPrecise, future));
        assertThat(statuses(result)).containsExactly("REJECTED", "REJECTED", "REJECTED");
        assertThat(result.at("/collections/0/code").asString()).isEqualTo("CUSTOMER_NOT_ASSIGNED");
        assertThat(result.at("/collections/1/code").asString()).isEqualTo("INVALID_AMOUNT");
        assertThat(result.at("/collections/2/code").asString()).isEqualTo("INVALID_COLLECTION_TIME");
        assertThat(result.get("missing")).as("rejections account for their numbers").isEmpty();
        assertThat(cashPosition().get("ledgerBalance").asString()).isEqualTo("0.00");

        assertThat(statuses(sync(List.of(notMine)))).containsExactly("DUPLICATE");
        JsonNode rejected = api.get("/api/v1/field/collections?status=REJECTED", manager.token()).expect(200).data();
        assertThat(rejected.get("items")).hasSize(3);
    }

    @Test
    void lateSyncsAndOfflineCashAboveTheLimitRaiseAlerts() {
        Map<String, Object> old = collection(1, "600.00");
        old.put("collectedAt", Instant.now().minus(30, ChronoUnit.HOURS).toString());
        sync(List.of(old, collection(2, "500.00")));

        List<String> types = new ArrayList<>();
        alerts("OPEN").get("items").forEach(alert -> types.add(alert.get("alertType").asString()));
        assertThat(types).containsExactlyInAnyOrder("LATE_SYNC", "OFFLINE_LIMIT");

        JsonNode alert = alerts("OPEN").at("/items/0");
        api.post("/api/v1/field/alerts/" + alert.get("id").asString() + "/resolve", officer.token(), Map.of(
                "note", "Mine", "version", alert.get("version").asLong())).expectError(403, "ACCESS_DENIED");
        api.post("/api/v1/field/alerts/" + alert.get("id").asString() + "/resolve", manager.token(), Map.of(
                "note", "Officer was off sick; cash counted", "version", alert.get("version").asLong())).expect(200);
        assertThat(alerts("OPEN").get("items")).hasSize(1);
    }

    @Test
    void theOfficerHandsTheCashToATellerAndThePositionReconciles() {
        sync(List.of(collection(1, "120.00"), collection(2, "30.00")));
        fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");
        String key = UUID.randomUUID().toString();
        Map<String, Object> remit = Map.of("officerId", officer.id().toString(), "amount", "100.00");

        api.postIdempotent("/api/v1/field/remittances", teller.token(), UUID.randomUUID().toString(), Map.of(
                "officerId", officer.id().toString(), "amount", "150.01")).expectError(422, "REMITTANCE_ABOVE_CASH");
        Api.Response received = api.postIdempotent("/api/v1/field/remittances", teller.token(), key, remit)
                .expect(201);
        assertThat(received.data().get("officerCashAfter").asString()).isEqualTo("50.00");
        assertThat(received.data().get("reference").asString()).startsWith("REM-");
        Api.Response replayed = api.postIdempotent("/api/v1/field/remittances", teller.token(), key, remit)
                .expect(201);
        assertThat(replayed.header("Idempotency-Replayed")).isEqualTo("true");

        JsonNode position = cashPosition();
        assertThat(position.get("ledgerBalance").asString()).isEqualTo("50.00");
        assertThat(position.get("collected").asString()).isEqualTo("150.00");
        assertThat(position.get("remitted").asString()).isEqualTo("100.00");
        assertThat(position.get("remittedToday").asString()).isEqualTo("100.00");
        assertThat(position.get("reconciled").asBoolean()).isTrue();
        assertThat(api.get("/api/v1/teller/sessions/me", teller.token()).expect(200).data().get("currentBalance")
                .asString()).isEqualTo("100.00");
        assertThat(api.get("/api/v1/field/remittances", officer.token()).expect(200).data().get("items"))
                .hasSize(1);
    }

    @Test
    void suspendedOfficersRevokedPhonesAndOtherOfficersCannotCollect() {
        JsonNode profile = api.get("/api/v1/field/officers/" + officer.id(), manager.token()).expect(200).data();
        api.post("/api/v1/field/officers/" + officer.id() + "/status", manager.token(), Map.of("status", "SUSPENDED",
                "reason", "Under investigation", "version", profile.get("version").asLong())).expect(200);
        assertThat(sync(List.of(collection(1, "10.00"))).at("/collections/0/code").asString())
                .isEqualTo("OFFICER_SUSPENDED");

        JsonNode device = api.get("/api/v1/field/devices?officerId=" + officer.id(), manager.token()).expect(200)
                .data().get(0);
        api.post("/api/v1/field/devices/" + deviceId + "/revoke", manager.token(), Map.of("note", "Phone stolen",
                "version", device.get("version").asLong())).expect(200);
        api.post(SYNC, officer.token(), Map.of("deviceId", deviceId, "collections",
                List.of(collection(2, "10.00")))).expectError(403, "DEVICE_NOT_REGISTERED");

        StaffHandle other = fixtures.createStaff(tenant, "collector2", tenant.headOfficeId(), false, "FIELD_OFFICER");
        api.post("/api/v1/field/devices", other.token(), Map.of("deviceKey", "phone-other-1", "name", "Itel"))
                .expectError(403, "NOT_A_FIELD_OFFICER");
        api.post(SYNC, other.token(), Map.of("deviceId", deviceId)).expectError(403, "NOT_A_FIELD_OFFICER");
        api.post("/api/v1/field/assignments", officer.token(), Map.of("customerId", customerId,
                "officerId", officer.id().toString())).expectError(403, "ACCESS_DENIED");
    }

    // ---------------------------------------------------------------------------------------------------------

    private void registerOfficer(String maxOfflineAmount, int maxOfflineHours) {
        JsonNode registered = api.post("/api/v1/field/officers", manager.token(), Map.of(
                "staffId", officer.id().toString(), "maxOfflineAmount", maxOfflineAmount,
                "maxOfflineHours", maxOfflineHours)).expect(201).data();
        assertThat(registered.get("cashBalance").asString()).isEqualTo("0.00");
        assertThat(registered.get("currency").asString()).isEqualTo("GHS");
        api.post("/api/v1/field/officers", manager.token(), Map.of("staffId", officer.id().toString(),
                "maxOfflineAmount", "1.00", "maxOfflineHours", 1)).expectError(409, "OFFICER_EXISTS");
    }

    private Map<String, Object> collection(long sequenceNo, String amount) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("clientReference", UUID.randomUUID().toString());
        item.put("sequenceNo", sequenceNo);
        item.put("customerId", customerId);
        item.put("accountId", accountId);
        item.put("amount", amount);
        item.put("currency", "GHS");
        item.put("collectedAt", Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS)
                .toString());
        item.put("latitude", "5.603717");
        item.put("longitude", "-0.186964");
        return item;
    }

    private JsonNode sync(List<Map<String, Object>> collections) {
        return api.post(SYNC, officer.token(), Map.of("deviceId", deviceId, "collections", collections)).expect(200)
                .data();
    }

    private static List<String> statuses(JsonNode syncResult) {
        List<String> statuses = new ArrayList<>();
        syncResult.get("collections").forEach(result -> statuses.add(result.get("status").asString()));
        return statuses;
    }

    private JsonNode alerts(String status) {
        return api.get("/api/v1/field/alerts?status=" + status, manager.token()).expect(200).data();
    }

    private JsonNode cashPosition() {
        return api.get("/api/v1/field/officers/" + officer.id() + "/cash-position", manager.token()).expect(200)
                .data();
    }

    private String balance(String account) {
        return api.get("/api/v1/accounts/" + account, manager.token()).expect(200).data().get("ledgerBalance")
                .asString();
    }

    private long count(String sql) {
        return inTenant(tenant.id(), () -> jdbcClient.sql(sql).query(Long.class).single());
    }
}
