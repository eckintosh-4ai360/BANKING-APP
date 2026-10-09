package com.company.banking.teller;

import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.TrialBalanceRow;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.support.Api;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * Vaults, drawers, teller sessions and cash movements. The gate: a teller's expected cash is always the drawer's
 * ledger balance, and a session only closes clean when the count matches it.
 */
class TellerIT extends LedgerIntegrationTest {

    private static final String DEPOSITS = "/api/v1/transactions/deposits";
    private static final String WITHDRAWALS = "/api/v1/transactions/withdrawals";

    @Autowired
    private JdbcClient jdbcClient;

    private TenantHandle tenant;
    private StaffHandle manager;
    private StaffHandle supervisor;
    private StaffHandle teller;
    private String accountId;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        supervisor = fixtures.createStaff(tenant, "supervisor", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        UUID customer = fixtures.verifiedIndividual(officer, manager, tenant.headOfficeId(), "Ama");
        accountId = fixtures.openAccount(manager.token(), customer,
                fixtures.publishedProduct(tenant, "CURR01", "CURRENT", terms("0"))).get("id").asString();
    }

    @Test
    void aBalancedDayClosesCleanAndTheExpectedCashIsAlwaysTheLedger() {
        JsonNode session = fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");
        assertThat(session.get("status").asString()).isEqualTo("OPEN");
        assertThat(session.get("openingBalance").asString()).isEqualTo("0.00");

        Random random = new Random(7);
        BigDecimal expected = BigDecimal.ZERO;
        cash(DEPOSITS, "1000.00").expect(201);
        expected = expected.add(new BigDecimal("1000.00"));
        for (int i = 0; i < 12; i++) {
            String amount = BigDecimal.valueOf(1 + random.nextInt(9_000), 2).toPlainString();
            if (random.nextBoolean()) {
                cash(DEPOSITS, amount).expect(201);
                expected = expected.add(new BigDecimal(amount));
            } else {
                cash(WITHDRAWALS, amount).expect(201);
                expected = expected.subtract(new BigDecimal(amount));
            }
            JsonNode mine = api.get("/api/v1/teller/sessions/me", teller.token()).expect(200).data();
            assertThat(new BigDecimal(mine.get("currentBalance").asString())).isEqualByComparingTo(expected);
        }

        JsonNode mine = api.get("/api/v1/teller/sessions/me", teller.token()).expect(200).data();
        JsonNode closed = close(mine, Map.of("0.01", expected.movePointRight(2).intValueExact()));
        assertThat(closed.get("status").asString()).isEqualTo("CLOSED");
        assertThat(closed.get("difference").asString()).isEqualTo("0.00");
        assertThat(new BigDecimal(closed.get("expectedClosingBalance").asString())).isEqualByComparingTo(expected);
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId, "amount", "1.00"))
                .expectError(422, "TELLER_SESSION_REQUIRED");
    }

    @Test
    void aShortageWaitsForASupervisorAndIsPostedToTellerShortages() {
        JsonNode session = fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");
        cash(DEPOSITS, "500.00").expect(201);
        cash(WITHDRAWALS, "120.00").expect(201);

        JsonNode balancing = close(session, Map.of("200", 1, "100", 1, "50", 1, "20", 1, "5", 1));
        assertThat(balancing.get("status").asString()).isEqualTo("BALANCING");
        assertThat(balancing.get("expectedClosingBalance").asString()).isEqualTo("380.00");
        assertThat(balancing.get("countedBalance").asString()).isEqualTo("375.00");
        assertThat(balancing.get("difference").asString()).isEqualTo("-5.00");
        cash(DEPOSITS, "1.00").expectError(422, "TELLER_SESSION_REQUIRED");

        String path = "/api/v1/teller/sessions/" + session.get("id").asString() + "/accept-difference";
        api.post(path, teller.token(), Map.of("note", "Mine", "version", balancing.get("version").asLong()))
                .expectError(403, "ACCESS_DENIED");
        JsonNode closed = api.post(path, supervisor.token(), Map.of("note", "Counted twice; short GHS 5",
                "version", balancing.get("version").asLong())).expect(200).data();
        assertThat(closed.get("status").asString()).isEqualTo("CLOSED_WITH_DIFFERENCE");
        assertThat(closed.get("currentBalance").asString()).as("drawer now matches the count").isEqualTo("375.00");
        assertThat(glDebitBalance(SystemAccount.TELLER_SHORTAGES)).isEqualByComparingTo("5.00");
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.reconcile()).intact()).isTrue();
    }

    @Test
    void anOverageGoesToOtherIncome() {
        JsonNode session = fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");
        cash(DEPOSITS, "100.00").expect(201);
        JsonNode balancing = close(session, Map.of("50", 2, "1", 3));
        assertThat(balancing.get("difference").asString()).isEqualTo("3.00");
        api.post("/api/v1/teller/sessions/" + session.get("id").asString() + "/accept-difference", supervisor.token(),
                Map.of("note", "Customer left change", "version", balancing.get("version").asLong())).expect(200);
        String otherIncome = inTenant(tenant.id(), () -> chartOfAccounts.requireSystem(SystemAccount.OTHER_INCOME)
                .code());
        assertThat(trialBalanceRow(otherIncome).creditBalance()).isEqualByComparingTo("3.00");
    }

    @Test
    void aTellerWorksOneDrawerAtATimeAndADrawerHasOneTeller() {
        JsonNode session = fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");
        StaffHandle second = fixtures.createStaff(tenant, "teller2", tenant.headOfficeId(), false, "TELLER");
        api.post("/api/v1/teller/sessions", second.token(), Map.of("drawerId", session.get("drawerId").asString()))
                .expectError(409, "DRAWER_IN_USE");
        String otherDrawer = api.post("/api/v1/cash/drawers", manager.token(), Map.of("branchId",
                tenant.headOfficeId().toString(), "currency", "GHS", "code", "T99", "name", "Spare")).expect(201)
                .data().get("id").asString();
        api.post("/api/v1/teller/sessions", teller.token(), Map.of("drawerId", otherDrawer))
                .expectError(409, "SESSION_ALREADY_OPEN");
        api.post("/api/v1/cash/drawers", manager.token(), Map.of("branchId", tenant.headOfficeId().toString(),
                "currency", "GHS", "code", "T99", "name", "Again")).expectError(409, "DRAWER_EXISTS");
        api.post("/api/v1/cash/drawers", teller.token(), Map.of("branchId", tenant.headOfficeId().toString(),
                "currency", "GHS", "code", "T98", "name", "Mine")).expectError(403, "ACCESS_DENIED");
        api.post("/api/v1/teller/sessions", second.token(), Map.of("drawerId", otherDrawer, "openingCount",
                Map.of("denominations", Map.of("10", 1)))).expectError(422, "OPENING_COUNT_MISMATCH");
        api.post("/api/v1/teller/sessions", second.token(), Map.of("drawerId", otherDrawer, "openingCount",
                Map.of("denominations", Map.of("10", 0)))).expect(201);
    }

    @Test
    void cashMovesBetweenBankVaultsAndDrawersWithFourEyes() {
        api.post("/api/v1/cash/vaults", manager.token(), Map.of("branchId", tenant.headOfficeId().toString(),
                "currency", "GHS", "name", "Main vault")).expect(201);
        api.post("/api/v1/cash/vaults", manager.token(), Map.of("branchId", tenant.headOfficeId().toString(),
                "currency", "GHS", "name", "Second")).expectError(409, "VAULT_EXISTS");

        JsonNode fromBank = movement(manager, Map.of("movementType", "BANK_TO_VAULT", "currency", "GHS",
                "amount", "10000.00", "branchId", tenant.headOfficeId().toString()));
        decide(manager, fromBank, "approve").expectError(403, "FOUR_EYES_VIOLATION");
        assertThat(decide(supervisor, fromBank, "approve").expect(200).data().get("status").asString())
                .isEqualTo("COMPLETED");

        JsonNode session = fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");
        JsonNode buy = movement(teller, Map.of("movementType", "VAULT_TO_DRAWER", "currency", "GHS",
                "amount", "2500.00", "drawerId", session.get("drawerId").asString()));
        decide(teller, buy, "approve").expectError(403, "ACCESS_DENIED");
        decide(manager, buy, "approve").expect(200);
        assertThat(api.get("/api/v1/teller/sessions/me", teller.token()).expect(200).data().get("currentBalance")
                .asString()).isEqualTo("2500.00");
        cash(WITHDRAWALS, "100.00").expectError(422, "INSUFFICIENT_FUNDS");
        cash(DEPOSITS, "300.00").expect(201);
        cash(WITHDRAWALS, "250.00").expect(201);

        JsonNode sell = movement(teller, Map.of("movementType", "DRAWER_TO_VAULT", "currency", "GHS",
                "amount", "2600.00", "drawerId", session.get("drawerId").asString()));
        decide(manager, sell, "approve").expectError(422, "CASH_INSUFFICIENT");
        JsonNode rejected = decide(manager, sell, "reject").expect(200).data();
        assertThat(rejected.get("status").asString()).isEqualTo("REJECTED");
        JsonNode sellAll = movement(teller, Map.of("movementType", "DRAWER_TO_VAULT", "currency", "GHS",
                "amount", "2550.00", "drawerId", session.get("drawerId").asString()));
        decide(manager, sellAll, "approve").expect(200);

        JsonNode mine = api.get("/api/v1/teller/sessions/me", teller.token()).expect(200).data();
        assertThat(mine.get("currentBalance").asString()).isEqualTo("0.00");
        assertThat(close(mine, Map.of("1", 0)).get("status").asString()).isEqualTo("CLOSED");
        JsonNode vault = api.get("/api/v1/cash/vaults", manager.token()).expect(200).data().get(0);
        assertThat(vault.get("balance").asString()).isEqualTo("10050.00");
        JsonNode tooMuch = movement(manager, Map.of("movementType", "VAULT_TO_BANK", "currency", "GHS",
                "amount", "10050.01", "branchId", tenant.headOfficeId().toString()));
        decide(supervisor, tooMuch, "approve").expectError(422, "CASH_INSUFFICIENT");
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.reconcile()).intact()).isTrue();
    }

    @Test
    void cashTravelsBetweenBranchesThroughCashInTransit() {
        UUID kumasi = fixtures.createBranch(tenant, "KUM");
        StaffHandle kumasiManager = fixtures.createStaff(tenant, "kumasi.manager", kumasi, false, "BRANCH_MANAGER");
        api.post("/api/v1/cash/vaults", manager.token(), Map.of("branchId", tenant.headOfficeId().toString(),
                "currency", "GHS", "name", "HQ vault")).expect(201);
        api.post("/api/v1/cash/vaults", kumasiManager.token(), Map.of("branchId", kumasi.toString(),
                "currency", "GHS", "name", "Kumasi vault")).expect(201);
        decide(supervisor, movement(manager, Map.of("movementType", "BANK_TO_VAULT", "currency", "GHS",
                "amount", "5000.00", "branchId", tenant.headOfficeId().toString())), "approve").expect(200);

        JsonNode shipment = movement(manager, Map.of("movementType", "VAULT_TO_VAULT", "currency", "GHS",
                "amount", "1200.00", "branchId", tenant.headOfficeId().toString(), "toBranchId", kumasi.toString()));
        JsonNode inTransit = decide(supervisor, shipment, "approve").expect(200).data();
        assertThat(inTransit.get("status").asString()).isEqualTo("IN_TRANSIT");
        assertThat(glDebitBalance(SystemAccount.CASH_IN_TRANSIT)).isEqualByComparingTo("1200.00");
        decide(manager, inTransit, "receive").expectError(404, "RESOURCE_NOT_FOUND");

        JsonNode received = decide(kumasiManager, inTransit, "receive").expect(200).data();
        assertThat(received.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(glDebitBalance(SystemAccount.CASH_IN_TRANSIT)).isEqualByComparingTo("0");
        assertThat(api.get("/api/v1/cash/vaults", kumasiManager.token()).expect(200).data().get(0).get("balance")
                .asString()).isEqualTo("1200.00");
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.reconcile()).intact()).isTrue();
    }

    @Test
    void endOfDayWaitsForEveryDrawerToClose() {
        JsonNode session = fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");
        api.post("/api/v1/operations/eod", tenant.adminToken(), null).expectError(422, "EOD_CHECKS_FAILED");
        close(session, Map.of("1", 0));
        assertThat(api.post("/api/v1/operations/eod", tenant.adminToken(), null).expect(202).data().get("status")
                .asString()).isEqualTo("COMPLETED");
    }

    @Test
    void endOfDayReconcilesEveryDrawerAndVaultAndFlagsCashPostedAfterTheCount() {
        api.post("/api/v1/cash/vaults", manager.token(), Map.of("branchId", tenant.headOfficeId().toString(),
                "currency", "GHS", "name", "Main vault")).expect(201);
        JsonNode idle = api.post("/api/v1/cash/drawers", manager.token(), Map.of("branchId",
                tenant.headOfficeId().toString(), "currency", "GHS", "code", "IDLE", "name", "Spare till"))
                .expect(201).data();

        // Counted to the cent, then credited behind the till's back.
        JsonNode counted = fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");
        cash(DEPOSITS, "300.00").expect(201);
        assertThat(close(counted, Map.of("100", 3)).get("status").asString()).isEqualTo("CLOSED");
        String countedDrawer = counted.get("drawerId").asString();
        UUID drawerLedger = inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT ledger_account_id FROM core.cash_drawer WHERE id = :id")
                .param("id", UUID.fromString(countedDrawer)).query(UUID.class).single());
        post(tenant.id(), tenant.headOfficeId(), List.of(
                PostingLine.toLedgerAccount(drawerLedger, EntryDirection.DEBIT, money("10.00"), "Unexplained"),
                PostingLine.toSystemAccount(SystemAccount.SUSPENSE_CREDIT, tenant.headOfficeId(), "GHS",
                        EntryDirection.CREDIT, money("10.00"), "Unexplained")));

        // Short by 5.00, accepted by a supervisor: the shortage posting brings the ledger to the count.
        StaffHandle second = fixtures.createStaff(tenant, "teller2", tenant.headOfficeId(), false, "TELLER");
        JsonNode shortSession = fixtures.openTill(manager, second, tenant.headOfficeId(), "GHS");
        api.postIdempotent(DEPOSITS, second.token(), key(), Map.of("accountId", accountId, "amount", "200.00"))
                .expect(201);
        JsonNode balancing = api.post("/api/v1/teller/sessions/" + shortSession.get("id").asString() + "/close",
                second.token(), Map.of("count", Map.of("denominations", Map.of("100", 1, "50", 1, "20", 2, "5", 1)),
                        "version", shortSession.get("version").asLong())).expect(200).data();
        api.post("/api/v1/teller/sessions/" + shortSession.get("id").asString() + "/accept-difference",
                supervisor.token(), Map.of("note", "Short GHS 5", "version", balancing.get("version").asLong()))
                .expect(200);

        JsonNode run = api.post("/api/v1/operations/eod", tenant.adminToken(), null).expect(202).data();
        assertThat(run.get("status").asString()).as("a cash break does not stop end-of-day").isEqualTo("COMPLETED");
        JsonNode positions = api.get("/api/v1/cash/positions", manager.token()).expect(200).data();
        assertThat(positions).hasSize(4);
        assertThat(positions).allSatisfy(position -> assertThat(position.get("businessDate").asString())
                .isEqualTo(run.get("businessDate").asString()));
        assertThat(position(positions, countedDrawer)).isEqualTo(List.of("DRAWER", "310.00", "300.00", "-10.00",
                "BREAK", counted.get("id").asString()));
        assertThat(position(positions, shortSession.get("drawerId").asString())).isEqualTo(List.of("DRAWER",
                "195.00", "195.00", "0.00", "MATCHED", shortSession.get("id").asString()));
        assertThat(position(positions, idle.get("id").asString())).isEqualTo(List.of("DRAWER", "0.00", "null",
                "null", "NOT_COUNTED", "null"));
        assertThat(positions).filteredOn(position -> position.get("cashPointType").asString().equals("VAULT"))
                .singleElement().satisfies(vault -> assertThat(vault.get("status").asString())
                        .isEqualTo("NOT_COUNTED"));
        assertThat(inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT count(*) FROM core.audit_log WHERE action = 'CASH_RECONCILIATION_BREAK'")
                .query(Long.class).single())).isEqualTo(1L);
        StaffHandle lender = fixtures.createStaff(tenant, "lender", tenant.headOfficeId(), false, "LOAN_OFFICER");
        api.get("/api/v1/cash/positions", lender.token()).expectError(403, "ACCESS_DENIED");
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Type, ledger balance, counted balance, difference, status and session of a cash point's position. */
    private static List<String> position(JsonNode positions, String cashPointId) {
        for (JsonNode position : positions) {
            if (position.get("cashPointId").asString().equals(cashPointId)) {
                return List.of(position.get("cashPointType").asString(), text(position, "ledgerBalance"),
                        text(position, "countedBalance"), text(position, "difference"), text(position, "status"),
                        text(position, "tellerSessionId"));
            }
        }
        throw new AssertionError("No position for " + cashPointId);
    }

    private static String text(JsonNode node, String field) {
        return node.get(field) == null || node.get(field).isNull() ? "null" : node.get(field).asString();
    }

    private Api.Response cash(String path, String amount) {
        return api.postIdempotent(path, teller.token(), key(), Map.of("accountId", accountId, "amount", amount));
    }

    private JsonNode close(JsonNode session, Map<String, Integer> denominations) {
        return api.post("/api/v1/teller/sessions/" + session.get("id").asString() + "/close", teller.token(),
                Map.of("count", Map.of("denominations", denominations), "version", session.get("version").asLong()))
                .expect(200).data();
    }

    private JsonNode movement(StaffHandle requester, Map<String, Object> body) {
        return api.post("/api/v1/cash/movements", requester.token(), body).expect(201).data();
    }

    private Api.Response decide(StaffHandle actor, JsonNode movement, String action) {
        return api.post("/api/v1/cash/movements/" + movement.get("id").asString() + "/" + action, actor.token(),
                Map.of("note", "Checked", "version", movement.get("version").asLong()));
    }

    private BigDecimal glDebitBalance(SystemAccount account) {
        String code = inTenant(tenant.id(), () -> chartOfAccounts.requireSystem(account).code());
        TrialBalanceRow row = trialBalanceRow(code);
        return row.debitBalance().subtract(row.creditBalance());
    }

    private TrialBalanceRow trialBalanceRow(String code) {
        return inTenant(tenant.id(), () -> ledgerQueries.trialBalance(null, "GHS", null)).rows().stream()
                .filter(row -> row.code().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
