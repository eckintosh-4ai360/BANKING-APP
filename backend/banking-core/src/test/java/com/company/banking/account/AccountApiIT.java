package com.company.banking.account;

import static com.company.banking.support.ProductRequests.product;
import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.account.service.AccountHoldService;
import com.company.banking.common.sequence.CheckDigits;
import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.exception.LedgerErrorCode;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * Deposit accounts end to end: opening rules, product versions, holds, the lifecycle and branch scope, plus the
 * database rules behind them.
 */
class AccountApiIT extends LedgerIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private AccountHoldService holdService;

    private TenantHandle tenant;
    private StaffHandle officer;
    private StaffHandle manager;
    private StaffHandle teller;
    private UUID customer;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        customer = fixtures.verifiedIndividual(officer, manager, tenant.headOfficeId(), "Ama");
    }

    @Test
    void openingChecksTheCustomerTheProductAndTheOwnership() {
        String savings = publishedProduct("SAVE01", "SAVINGS", terms("50.00"));
        String draftOnly = api.post("/api/v1/products", tenant.adminToken(), product("DRAFT1", "SAVINGS", terms("0")))
                .expect(201).data().get("id").asString();
        Map<String, Object> enhancedOnly = terms("0");
        enhancedOnly.put("requiredKycTier", "TIER_3");
        String premium = publishedProduct("PREM01", "SAVINGS", enhancedOnly);
        UUID unverified = UUID.fromString(fixtures.createIndividual(officer.token(), tenant.headOfficeId(), "Kofi",
                "Boateng", "+233244999888").get("id").asString());
        UUID other = fixtures.verifiedIndividual(officer, manager, tenant.headOfficeId(), "Yaw");

        api.post("/api/v1/accounts", manager.token(), single(unverified, savings))
                .expectError(422, "CUSTOMER_NOT_ELIGIBLE");
        api.post("/api/v1/accounts", manager.token(), single(customer, draftOnly))
                .expectError(422, "PRODUCT_NOT_AVAILABLE");
        api.post("/api/v1/accounts", manager.token(), single(customer, premium))
                .expectError(422, "KYC_TIER_TOO_LOW");
        api.post("/api/v1/accounts", manager.token(), opening(customer, savings, "SINGLE", holder(other, "JOINT")))
                .expectError(422, "INVALID_OWNERSHIP");
        api.post("/api/v1/accounts", manager.token(), opening(customer, savings, "JOINT_ANY"))
                .expectError(422, "INVALID_OWNERSHIP");
        api.post("/api/v1/accounts", manager.token(), opening(customer, savings, "BUSINESS"))
                .expectError(422, "INVALID_OWNERSHIP");
        api.post("/api/v1/accounts", manager.token(), opening(customer, savings, "JOINT_ANY",
                holder(customer, "JOINT"))).expectError(422, "INVALID_OWNERSHIP");
        api.post("/api/v1/accounts", teller.token(), single(customer, savings)).expectError(403, "ACCESS_DENIED");

        JsonNode opened = api.post("/api/v1/accounts", manager.token(), single(customer, savings)).expect(201).data();
        String number = opened.get("accountNumber").asString();
        assertThat(number).matches("^1[0-9]{9}$");
        assertThat(CheckDigits.isValidLuhn(number)).isTrue();
        assertThat(opened.get("status").asString()).as("waiting for the opening deposit").isEqualTo("PENDING");
        assertThat(opened.get("title").asString()).isEqualTo("Ama Mensah");
        assertThat(opened.get("productCode").asString()).isEqualTo("SAVE01");
        assertThat(opened.get("currency").asString()).isEqualTo("GHS");
        assertThat(opened.get("branchId").asString()).isEqualTo(tenant.headOfficeId().toString());
        assertThat(opened.get("ledgerBalance").asString()).isEqualTo("0.00");
        assertThat(opened.get("availableBalance").asString()).isEqualTo("0.00");
        assertThat(opened.at("/holders/0/role").asString()).isEqualTo("PRIMARY");
        assertThat(opened.at("/holders/0/customerId").asString()).isEqualTo(customer.toString());

        JsonNode second = api.post("/api/v1/accounts", manager.token(), single(customer, savings)).expect(201).data();
        assertThat(second.get("accountNumber").asString()).isNotEqualTo(number);
        assertThat(api.get("/api/v1/customers/" + customer + "/accounts", teller.token()).expect(200).data())
                .hasSize(2);
        assertThat(api.get("/api/v1/accounts?accountNumber=" + number, teller.token()).expect(200).data()
                .get("items")).singleElement().satisfies(item ->
                assertThat(item.get("id").asString()).isEqualTo(opened.get("id").asString()));
        assertThat(auditCount("ACCOUNT_OPENED", opened.get("id").asString())).isEqualTo(1);
    }

    @Test
    void jointHoldersAreRecordedAndAnOpenAccountKeepsItsOwnersFromBeingClosed() {
        String current = publishedProduct("CURR01", "CURRENT", terms("0"));
        UUID partner = fixtures.verifiedIndividual(officer, manager, tenant.headOfficeId(), "Esi");

        JsonNode joint = api.post("/api/v1/accounts", manager.token(),
                opening(customer, current, "JOINT_ALL", holder(partner, "JOINT"))).expect(201).data();
        assertThat(joint.get("ownershipType").asString()).isEqualTo("JOINT_ALL");
        assertThat(joint.get("holders")).hasSize(2);
        assertThat(joint.at("/holders/1/role").asString()).isEqualTo("JOINT");
        assertThat(api.get("/api/v1/customers/" + partner + "/accounts", manager.token()).expect(200).data())
                .hasSize(1);

        api.post("/api/v1/customers/" + partner + "/status", manager.token(), closeCustomer(partner))
                .expectError(422, "CUSTOMER_HAS_OPEN_HOLDINGS");

        api.post(path(joint, "close"), manager.token(), Map.of("reason", "Customers asked to close",
                "version", joint.get("version").asLong())).expect(200);
        api.post("/api/v1/customers/" + partner + "/status", manager.token(), closeCustomer(partner)).expect(200);
    }

    @Test
    void aPendingAccountActivatesOnlyOnceItsOpeningDepositIsIn() {
        String savings = publishedProduct("SAVE01", "SAVINGS", terms("50.00"));
        JsonNode account = api.post("/api/v1/accounts", manager.token(), single(customer, savings)).expect(201).data();

        api.post(path(account, "status"), manager.token(), status("ACTIVE", account))
                .expectError(422, "OPENING_BALANCE_NOT_MET");
        fund(account, "50.00");

        JsonNode active = api.post(path(account, "status"), manager.token(), status("ACTIVE", account))
                .expect(200).data();
        assertThat(active.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(active.get("activatedAt").isNull()).isFalse();
        assertThat(active.get("ledgerBalance").asString()).isEqualTo("50.00");
        assertThat(auditCount("ACCOUNT_STATUS_CHANGED", account.get("id").asString())).isEqualTo(1);
    }

    @Test
    void accountsKeepTheTermsOfTheVersionTheyWereOpenedUnder() {
        JsonNode created = api.post("/api/v1/products", tenant.adminToken(), product("SAVE01", "SAVINGS",
                terms("0"))).expect(201).data();
        String productId = created.get("id").asString();
        String v1 = created.at("/versions/0/id").asString();
        api.post("/api/v1/products/" + productId + "/versions/" + v1 + "/publish", tenant.adminToken(), null)
                .expect(200);
        JsonNode early = api.post("/api/v1/accounts", manager.token(), single(customer, productId)).expect(201).data();

        String v2 = api.post("/api/v1/products/" + productId + "/versions", tenant.adminToken(), null)
                .expect(201).data().at("/versions/0/id").asString();
        api.put("/api/v1/products/" + productId + "/versions/" + v2, tenant.adminToken(), terms("20.00"))
                .expect(200);
        api.post("/api/v1/products/" + productId + "/versions/" + v2 + "/publish", tenant.adminToken(), null)
                .expect(200);
        JsonNode late = api.post("/api/v1/accounts", manager.token(), single(customer, productId)).expect(201).data();

        JsonNode earlyNow = api.get(path(early, null), manager.token()).expect(200).data();
        assertThat(earlyNow.get("productVersionId").asString()).isEqualTo(v1);
        assertThat(earlyNow.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(late.get("productVersionId").asString()).isEqualTo(v2);
        assertThat(late.get("status").asString()).isEqualTo("PENDING");
    }

    @Test
    void holdsReduceTheAvailableBalanceAndCannotExceedIt() {
        JsonNode account = activeAccount();
        UUID ledgerAccount = ledgerAccountOf(account);
        fund(account, "100.00");

        JsonNode hold = api.post(path(account, "holds"), manager.token(), Map.of("amount", "30.00",
                "holdType", "LIEN", "reason", "Guarantee for a group loan")).expect(201).data();
        assertThat(hold.get("status").asString()).isEqualTo("ACTIVE");
        JsonNode held = api.get(path(account, null), teller.token()).expect(200).data();
        assertThat(held.get("ledgerBalance").asString()).isEqualTo("100.00");
        assertThat(held.get("holdAmount").asString()).isEqualTo("30.00");
        assertThat(held.get("availableBalance").asString()).isEqualTo("70.00");

        api.post(path(account, "holds"), manager.token(), Map.of("amount", "80.00", "holdType", "LEGAL",
                "reason", "Court order")).expectError(422, "INSUFFICIENT_FUNDS_FOR_HOLD");
        api.post(path(account, "holds"), manager.token(), Map.of("amount", "1.00", "holdType", "PENDING_PAYMENT",
                "reason", "Only the payment module places these")).expect(400);
        api.post(path(account, "holds"), teller.token(), Map.of("amount", "1.00", "holdType", "LIEN",
                "reason", "Tellers cannot")).expectError(403, "ACCESS_DENIED");

        assertFailsWith(LedgerErrorCode.INSUFFICIENT_FUNDS,
                () -> withdraw(tenant.id(), ledgerAccount, tenant.headOfficeId(), "GHS", "70.01"));
        withdraw(tenant.id(), ledgerAccount, tenant.headOfficeId(), "GHS", "70.00");

        JsonNode released = api.post(path(account, "holds/" + hold.get("id").asString() + "/release"),
                manager.token(), Map.of("reason", "Loan repaid", "version", hold.get("version").asLong()))
                .expect(200).data();
        assertThat(released.get("status").asString()).isEqualTo("RELEASED");
        assertThat(released.get("releasedBy").asString()).isEqualTo(manager.id().toString());
        api.post(path(account, "holds/" + hold.get("id").asString() + "/release"), manager.token(),
                Map.of("reason", "Again", "version", released.get("version").asLong()))
                .expectError(422, "INVALID_STATE_TRANSITION");

        assertThat(api.get(path(account, null), teller.token()).expect(200).data().get("availableBalance")
                .asString()).isEqualTo("30.00");
        assertThat(api.get(path(account, "holds"), teller.token()).expect(200).data()).hasSize(1);
        assertThat(auditCount("ACCOUNT_HOLD_PLACED", hold.get("id").asString())).isEqualTo(1);
        assertThat(auditCount("ACCOUNT_HOLD_RELEASED", hold.get("id").asString())).isEqualTo(1);
    }

    @Test
    void anExpiredHoldGivesItsFundsBack() throws InterruptedException {
        JsonNode account = activeAccount();
        fund(account, "40.00");
        Instant expiry = Instant.now().plusMillis(1500);
        api.post(path(account, "holds"), manager.token(), Map.of("amount", "40.00", "holdType", "FRAUD_REVIEW",
                "reason", "Unusual activity", "expiresAt", expiry.toString())).expect(201);
        assertThat(inTenant(tenant.id(), () -> holdService.expireDue(100))).as("not yet").isZero();

        Thread.sleep(Math.max(0, expiry.toEpochMilli() - System.currentTimeMillis()) + 50);
        assertThat(inTenant(tenant.id(), () -> holdService.expireDue(100))).isEqualTo(1);
        assertThat(api.get(path(account, "holds"), manager.token()).expect(200).data().at("/0/status").asString())
                .isEqualTo("EXPIRED");
        assertThat(api.get(path(account, null), manager.token()).expect(200).data().get("availableBalance")
                .asString()).isEqualTo("40.00");
    }

    @Test
    void closingNeedsAnEmptyUnfrozenAccountAndClosesItsLedgerAccount() {
        JsonNode account = activeAccount();
        UUID ledgerAccount = ledgerAccountOf(account);
        fund(account, "40.00");

        api.post(path(account, "close"), manager.token(), close(account)).expectError(422, "ACCOUNT_NOT_EMPTY");
        withdraw(tenant.id(), ledgerAccount, tenant.headOfficeId(), "GHS", "40.00");

        JsonNode frozen = api.post(path(account, "status"), manager.token(), status("FROZEN", account))
                .expect(200).data();
        assertThat(frozen.get("status").asString()).isEqualTo("FROZEN");
        api.post(path(account, "close"), manager.token(), close(frozen))
                .expectError(422, "INVALID_STATE_TRANSITION");
        JsonNode restricted = api.post(path(account, "status"), manager.token(), status("RESTRICTED", frozen))
                .expect(200).data();
        api.post(path(account, "close"), teller.token(), close(restricted)).expectError(403, "ACCESS_DENIED");

        JsonNode closed = api.post(path(account, "close"), manager.token(), close(restricted)).expect(200).data();
        assertThat(closed.get("status").asString()).isEqualTo("CLOSED");
        assertThat(closed.get("closedOn").isNull()).isFalse();
        assertThat(inTenant(tenant.id(), () -> ledgerAccounts.get(ledgerAccount).status())).isEqualTo("CLOSED");
        assertFailsWith(LedgerErrorCode.LEDGER_ACCOUNT_CLOSED,
                () -> deposit(tenant.id(), ledgerAccount, tenant.headOfficeId(), "GHS", "1.00"));

        api.post(path(account, "status"), manager.token(), status("ACTIVE", closed))
                .expectError(422, "ACCOUNT_CLOSED");
        api.post(path(account, "close"), manager.token(), close(closed)).expectError(422, "ACCOUNT_CLOSED");
        api.post(path(account, "holds"), manager.token(), Map.of("amount", "1.00", "holdType", "LIEN",
                "reason", "Too late")).expectError(422, "ACCOUNT_CLOSED");
        assertThat(auditCount("ACCOUNT_CLOSED", account.get("id").asString())).isEqualTo(1);
        api.post("/api/v1/customers/" + customer + "/status", manager.token(), closeCustomer(customer)).expect(200);
    }

    @Test
    void staffOnlyReachAccountsOfTheirBranches() {
        UUID kumasi = fixtures.createBranch(tenant, "KUM");
        StaffHandle kumasiManager = fixtures.createStaff(tenant, "kumasi.manager", kumasi, false, "BRANCH_MANAGER");
        String current = publishedProduct("CURR01", "CURRENT", terms("0"));
        JsonNode account = api.post("/api/v1/accounts", manager.token(), single(customer, current)).expect(201).data();

        api.get(path(account, null), kumasiManager.token()).expectError(404, "RESOURCE_NOT_FOUND");
        api.get(path(account, "holds"), kumasiManager.token()).expectError(404, "RESOURCE_NOT_FOUND");
        api.post(path(account, "status"), kumasiManager.token(), status("FROZEN", account))
                .expectError(404, "RESOURCE_NOT_FOUND");
        assertThat(api.get("/api/v1/accounts", kumasiManager.token()).expect(200).data().get("items")).isEmpty();
        api.post("/api/v1/accounts", kumasiManager.token(), single(customer, current))
                .expectError(404, "RESOURCE_NOT_FOUND");

        Map<String, Object> elsewhere = new HashMap<>(single(customer, current));
        elsewhere.put("branchId", kumasi.toString());
        api.post("/api/v1/accounts", manager.token(), elsewhere).expectError(404, "RESOURCE_NOT_FOUND");
        assertThat(api.get("/api/v1/accounts", manager.token()).expect(200).data().get("items")).hasSize(1);
    }

    @Test
    void theDatabaseKeepsHoldTotalsAndNeverLosesAHold() {
        JsonNode account = activeAccount();
        UUID ledgerAccount = ledgerAccountOf(account);
        fund(account, "100.00");
        JsonNode hold = api.post(path(account, "holds"), manager.token(), Map.of("amount", "25.00",
                "holdType", "LIEN", "reason", "Collateral")).expect(201).data();
        UUID holdId = UUID.fromString(hold.get("id").asString());

        assertThat(holdAmount(ledgerAccount)).isEqualByComparingTo("25.00");
        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql(
                        "DELETE FROM core.account_hold WHERE id = :id").param("id", holdId).update()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql(
                        "UPDATE core.account_hold SET amount = 1 WHERE id = :id").param("id", holdId).update()))
                .isInstanceOf(DataAccessException.class);
        // A hold written straight to the table still cannot reserve more than the account holds.
        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql("""
                        INSERT INTO core.account_hold (id, tenant_id, account_id, ledger_account_id, amount,
                                                       hold_type, status, reason, placed_at)
                        VALUES (:id, :tenantId, :accountId, :ledgerAccountId, 75.01, 'LEGAL', 'ACTIVE', 'Direct',
                                now())""")
                .param("id", UUID.randomUUID())
                .param("tenantId", tenant.id())
                .param("accountId", UUID.fromString(account.get("id").asString()))
                .param("ledgerAccountId", ledgerAccount)
                .update()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_account_balance_no_overdraft");

        api.post(path(account, "holds/" + holdId + "/release"), manager.token(), Map.of("reason", "Done",
                "version", hold.get("version").asLong())).expect(200);
        assertThat(holdAmount(ledgerAccount)).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------------------------------------------------

    private String publishedProduct(String code, String type, Map<String, Object> terms) {
        JsonNode created = api.post("/api/v1/products", tenant.adminToken(), product(code, type, terms))
                .expect(201).data();
        String productId = created.get("id").asString();
        api.post("/api/v1/products/" + productId + "/versions/" + created.at("/versions/0/id").asString()
                + "/publish", tenant.adminToken(), null).expect(200);
        return productId;
    }

    private JsonNode activeAccount() {
        String current = publishedProduct("CURR01", "CURRENT", terms("0"));
        JsonNode account = api.post("/api/v1/accounts", manager.token(), single(customer, current)).expect(201).data();
        assertThat(account.get("status").asString()).isEqualTo("ACTIVE");
        return account;
    }

    private UUID ledgerAccountOf(JsonNode account) {
        return inTenant(tenant.id(), () -> jdbcClient.sql("SELECT ledger_account_id FROM core.account WHERE id = :id")
                .param("id", UUID.fromString(account.get("id").asString()))
                .query(UUID.class)
                .single());
    }

    private void fund(JsonNode account, String amount) {
        deposit(tenant.id(), ledgerAccountOf(account), tenant.headOfficeId(), "GHS", amount);
    }

    private BigDecimal holdAmount(UUID ledgerAccount) {
        return inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT hold_amount FROM core.account_balance WHERE ledger_account_id = :id")
                .param("id", ledgerAccount)
                .query(BigDecimal.class)
                .single());
    }

    private long auditCount(String action, String resourceId) {
        return inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT count(*) FROM core.audit_log WHERE action = :action AND resource_id = :resourceId")
                .param("action", action)
                .param("resourceId", resourceId)
                .query(Long.class)
                .single());
    }

    private Map<String, Object> closeCustomer(UUID customerId) {
        long version = api.get("/api/v1/customers/" + customerId, manager.token()).expect(200).data()
                .get("version").asLong();
        return Map.of("status", "CLOSED", "reason", "Relationship ended", "version", version);
    }

    private static Map<String, Object> single(UUID customerId, String productId) {
        return opening(customerId, productId, "SINGLE");
    }

    @SafeVarargs
    private static Map<String, Object> opening(UUID customerId, String productId, String ownership,
                                               Map<String, Object>... others) {
        return Map.of("customerId", customerId.toString(), "productId", productId, "ownershipType", ownership,
                "otherHolders", List.of(others));
    }

    private static Map<String, Object> holder(UUID customerId, String role) {
        return Map.of("customerId", customerId.toString(), "role", role);
    }

    private static Map<String, Object> status(String status, JsonNode account) {
        return Map.of("status", status, "reason", "Branch decision", "version", account.get("version").asLong());
    }

    private static Map<String, Object> close(JsonNode account) {
        return Map.of("reason", "Customer request", "version", account.get("version").asLong());
    }

    private static String path(JsonNode account, String action) {
        String base = "/api/v1/accounts/" + account.get("id").asString();
        return action == null ? base : base + "/" + action;
    }
}
