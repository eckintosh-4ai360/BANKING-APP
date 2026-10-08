package com.company.banking.approval;

import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.dto.ChartOfAccountResponse;
import com.company.banking.ledger.dto.JournalResponse;
import com.company.banking.ledger.dto.TrialBalanceRow;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.support.Api;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import java.math.BigDecimal;
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
 * Maker-checker: thresholds on withdrawals and transfers, reversals and manual journals that only happen after a
 * second person approves, four eyes, branch scope, and the database rules behind them.
 */
class ApprovalIT extends LedgerIntegrationTest {

    private static final String DEPOSITS = "/api/v1/transactions/deposits";
    private static final String WITHDRAWALS = "/api/v1/transactions/withdrawals";
    private static final String TRANSFERS = "/api/v1/transactions/transfers";

    @Autowired
    private JdbcClient jdbcClient;

    private TenantHandle tenant;
    private StaffHandle manager;
    private StaffHandle supervisor;
    private StaffHandle teller;
    private UUID customer;
    private String current;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        supervisor = fixtures.createStaff(tenant, "supervisor", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        customer = fixtures.verifiedIndividual(officer, manager, tenant.headOfficeId(), "Ama");
        current = fixtures.publishedProduct(tenant, "CURR01", "CURRENT", terms("0"));
    }

    @Test
    void aWithdrawalAtTheThresholdWaitsForACheckerAndPostsWhenApproved() {
        threshold("CASH_WITHDRAWAL", "1000.00");
        String accountId = funded("5000.00");

        api.postIdempotent(WITHDRAWALS, teller.token(), key(), withdrawal(accountId, "999.99")).expect(201);
        String key = key();
        Api.Response held = api.postIdempotent(WITHDRAWALS, teller.token(), key, withdrawal(accountId, "1000.00"))
                .expect(202);
        assertThat(held.data().get("outcome").asString()).isEqualTo("PENDING_APPROVAL");
        assertThat(held.data().get("transaction").isNull()).isTrue();
        JsonNode approval = held.data().get("approval");
        assertThat(approval.get("requestType").asString()).isEqualTo("CASH_WITHDRAWAL");
        assertThat(approval.get("amount").asString()).isEqualTo("1000.00");
        assertThat(approval.get("requestedBy").asString()).isEqualTo(teller.id().toString());
        assertThat(balance(accountId)).as("nothing moves before approval").isEqualTo("4000.01");

        Api.Response retried = api.postIdempotent(WITHDRAWALS, teller.token(), key, withdrawal(accountId, "1000.00"))
                .expect(202);
        assertThat(retried.header("Idempotency-Replayed")).isEqualTo("true");
        assertThat(retried.data().at("/approval/id").asString()).isEqualTo(approval.get("id").asString());

        String approvalId = approval.get("id").asString();
        api.get("/api/v1/approvals/" + approvalId, teller.token()).expect(200);
        assertThat(api.get("/api/v1/approvals?status=PENDING", manager.token()).expect(200).data().get("items"))
                .hasSize(1);
        api.post(decide(approvalId, "approve"), teller.token(), decision(approval)).expectError(403, "ACCESS_DENIED");

        JsonNode approved = api.post(decide(approvalId, "approve"), manager.token(), decision(approval))
                .expect(200).data();
        assertThat(approved.get("status").asString()).isEqualTo("APPROVED");
        assertThat(approved.get("decidedBy").asString()).isEqualTo(manager.id().toString());
        assertThat(balance(accountId)).isEqualTo("3000.01");

        String transactionId = approved.get("resultResourceId").asString();
        JsonNode transaction = api.get("/api/v1/transactions/" + transactionId, manager.token()).expect(200).data();
        assertThat(transaction.get("initiatedBy").asString()).isEqualTo(teller.id().toString());
        assertThat(transaction.get("approvedBy").asString()).isEqualTo(manager.id().toString());
        assertThat(transaction.get("approvalRequestId").asString()).isEqualTo(approvalId);
        JournalResponse journal = inTenant(tenant.id(), () -> ledgerQueries.journal(
                UUID.fromString(transaction.get("journalEntryId").asString())));
        assertThat(journal.postedBy()).as("the maker").isEqualTo(teller.id());
        assertThat(journal.approvedBy()).as("the checker").isEqualTo(manager.id());

        api.post(decide(approvalId, "approve"), supervisor.token(), decision(approved))
                .expectError(422, "APPROVAL_NOT_PENDING");
        assertThat(balance(accountId)).as("approved once, posted once").isEqualTo("3000.01");
        assertThat(auditCount("APPROVAL_APPROVED", approvalId)).isEqualTo(1);
    }

    @Test
    void anApprovedActionIsCheckedAgainWhenItRuns() {
        threshold("CASH_WITHDRAWAL", "500.00");
        String accountId = funded("800.00");
        JsonNode approval = api.postIdempotent(WITHDRAWALS, teller.token(), key(), withdrawal(accountId, "600.00"))
                .expect(202).data().get("approval");
        api.postIdempotent(WITHDRAWALS, teller.token(), key(), withdrawal(accountId, "499.00")).expect(201);

        String approvalId = approval.get("id").asString();
        api.post(decide(approvalId, "approve"), manager.token(), decision(approval))
                .expectError(422, "INSUFFICIENT_FUNDS");
        JsonNode stillPending = api.get("/api/v1/approvals/" + approvalId, manager.token()).expect(200).data();
        assertThat(stillPending.get("status").asString()).isEqualTo("PENDING");

        api.post(decide(approvalId, "reject"), manager.token(), Map.of("version", stillPending.get("version")
                .asLong())).expectError(422, "DECISION_NOTE_REQUIRED");
        JsonNode rejected = api.post(decide(approvalId, "reject"), manager.token(), Map.of("note",
                "Not enough money left", "version", stillPending.get("version").asLong())).expect(200).data();
        assertThat(rejected.get("status").asString()).isEqualTo("REJECTED");
        assertThat(balance(accountId)).isEqualTo("301.00");
    }

    @Test
    void aMakerMayCancelTheirOwnPendingTransferButNobodyElseCan() {
        threshold("TRANSFER", "100.00");
        String from = funded("1000.00");
        String to = fixtures.openAccount(manager.token(), customer, current).get("id").asString();
        JsonNode approval = api.postIdempotent(TRANSFERS, teller.token(), key(), Map.of("fromAccountId", from,
                "toAccountId", to, "amount", "150.00")).expect(202).data().get("approval");
        String approvalId = approval.get("id").asString();

        api.post(decide(approvalId, "cancel"), manager.token(), decision(approval))
                .expectError(403, "NOT_THE_REQUESTER");
        JsonNode cancelled = api.post(decide(approvalId, "cancel"), teller.token(), decision(approval))
                .expect(200).data();
        assertThat(cancelled.get("status").asString()).isEqualTo("CANCELLED");
        api.post(decide(approvalId, "approve"), manager.token(), decision(cancelled))
                .expectError(422, "APPROVAL_NOT_PENDING");
        assertThat(balance(from)).isEqualTo("1000.00");
        assertThat(balance(to)).isEqualTo("0.00");
    }

    @Test
    void aReversalMirrorsTheTransactionIncludingItsChargeOnlyAfterApproval() {
        Map<String, Object> charged = terms("0");
        charged.put("charges", List.of(Map.of("event", "CASH_WITHDRAWAL", "name", "Withdrawal fee",
                "calculation", "FLAT", "flatAmount", "2.00")));
        String product = fixtures.publishedProduct(tenant, "SAVE01", "SAVINGS", charged);
        String accountId = fixtures.openAccount(manager.token(), customer, product).get("id").asString();
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId, "amount", "500.00"))
                .expect(201);
        JsonNode withdrawn = api.postIdempotent(WITHDRAWALS, teller.token(), key(), withdrawal(accountId, "100.00"))
                .expect(201).data().get("transaction");
        String transactionId = withdrawn.get("id").asString();
        assertThat(balance(accountId)).isEqualTo("398.00");

        api.post("/api/v1/transactions/" + transactionId + "/reversal", teller.token(), Map.of("reason", "Teller"))
                .expectError(403, "ACCESS_DENIED");
        JsonNode approval = api.post("/api/v1/transactions/" + transactionId + "/reversal", manager.token(),
                Map.of("reason", "Paid to the wrong customer")).expect(202).data();
        api.post("/api/v1/transactions/" + transactionId + "/reversal", manager.token(),
                Map.of("reason", "Again")).expectError(409, "APPROVAL_ALREADY_PENDING");
        assertThat(balance(accountId)).as("nothing moves before approval").isEqualTo("398.00");

        String approvalId = approval.get("id").asString();
        api.post(decide(approvalId, "approve"), manager.token(), decision(approval))
                .expectError(403, "FOUR_EYES_VIOLATION");
        api.post(decide(approvalId, "approve"), supervisor.token(), decision(approval)).expect(200);

        assertThat(balance(accountId)).isEqualTo("500.00");
        JsonNode reversed = api.get("/api/v1/transactions/" + transactionId, manager.token()).expect(200).data();
        assertThat(reversed.get("status").asString()).isEqualTo("REVERSED");
        assertThat(reversed.get("reversedBy").asString()).isEqualTo(supervisor.id().toString());
        assertThat(reversed.get("reversalReason").asString()).isEqualTo("Paid to the wrong customer");
        JournalResponse mirror = inTenant(tenant.id(), () -> ledgerQueries.journal(
                UUID.fromString(reversed.get("reversalJournalEntryId").asString())));
        assertThat(mirror.sourceType()).isEqualTo("REVERSAL");
        assertThat(mirror.lines()).as("withdrawal and charge lines mirrored").hasSize(4);
        assertThat(mirror.postedBy()).isEqualTo(manager.id());
        assertThat(mirror.approvedBy()).isEqualTo(supervisor.id());
        assertThat(feeIncome()).isEqualByComparingTo("0");
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.reconcile()).intact()).isTrue();

        api.post("/api/v1/transactions/" + transactionId + "/reversal", manager.token(),
                Map.of("reason", "Once more")).expectError(422, "TRANSACTION_ALREADY_REVERSED");
        assertThat(auditCount("TRANSACTION_REVERSED", transactionId)).isEqualTo(1);
    }

    @Test
    void aSpentDepositCannotBeReversed() {
        String accountId = fixtures.openAccount(manager.token(), customer, current).get("id").asString();
        String depositId = api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId,
                "amount", "300.00")).expect(201).data().at("/transaction/id").asString();
        api.postIdempotent(WITHDRAWALS, teller.token(), key(), withdrawal(accountId, "250.00")).expect(201);

        JsonNode approval = api.post("/api/v1/transactions/" + depositId + "/reversal", manager.token(),
                Map.of("reason", "Counterfeit notes")).expect(202).data();
        api.post(decide(approval.get("id").asString(), "approve"), supervisor.token(), decision(approval))
                .expectError(422, "INSUFFICIENT_FUNDS");
        assertThat(balance(accountId)).isEqualTo("50.00");
        assertThat(api.get("/api/v1/transactions/" + depositId, manager.token()).expect(200).data().get("status")
                .asString()).isEqualTo("POSTED");
    }

    @Test
    void manualJournalsPostOnlyAfterASecondAccountantApproves() {
        StaffHandle maker = fixtures.createStaff(tenant, "accountant.a", tenant.headOfficeId(), true, "ACCOUNTANT");
        StaffHandle checker = fixtures.createStaff(tenant, "accountant.b", tenant.headOfficeId(), true,
                "ACCOUNTANT");
        String bank = gl("1140");
        String capital = gl("3100");

        api.post("/api/v1/ledger/manual-journals", maker.token(), journal(bank, "250.00", capital, "250.01"))
                .expectError(422, "UNBALANCED_JOURNAL");
        api.post("/api/v1/ledger/manual-journals", maker.token(), journal(gl("2110"), "250.00", capital, "250.00"))
                .expectError(422, "GL_ACCOUNT_NOT_MANUAL");
        api.post("/api/v1/ledger/manual-journals", teller.token(), journal(bank, "250.00", capital, "250.00"))
                .expectError(403, "ACCESS_DENIED");

        JsonNode approval = api.post("/api/v1/ledger/manual-journals", maker.token(),
                journal(bank, "250.00", capital, "250.00")).expect(202).data();
        assertThat(approval.get("requestType").asString()).isEqualTo("MANUAL_JOURNAL");
        assertThat(approval.get("amount").asString()).isEqualTo("250.00");
        assertThat(creditBalance("3100")).as("not posted yet").isEqualByComparingTo("0");

        String approvalId = approval.get("id").asString();
        api.post(decide(approvalId, "approve"), maker.token(), decision(approval))
                .expectError(403, "FOUR_EYES_VIOLATION");
        api.post(decide(approvalId, "approve"), manager.token(), decision(approval))
                .expectError(403, "ACCESS_DENIED");
        JsonNode approved = api.post(decide(approvalId, "approve"), checker.token(), decision(approval))
                .expect(200).data();

        JournalResponse journal = inTenant(tenant.id(), () -> ledgerQueries.journal(
                UUID.fromString(approved.get("resultResourceId").asString())));
        assertThat(journal.sourceType()).isEqualTo("MANUAL");
        assertThat(journal.postedBy()).isEqualTo(maker.id());
        assertThat(journal.approvedBy()).isEqualTo(checker.id());
        assertThat(creditBalance("3100")).isEqualByComparingTo("250.00");
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.reconcile()).intact()).isTrue();
    }

    @Test
    void checkersOnlyReachRequestsOfTheirBranches() {
        threshold("CASH_WITHDRAWAL", "100.00");
        UUID kumasi = fixtures.createBranch(tenant, "KUM");
        StaffHandle kumasiManager = fixtures.createStaff(tenant, "kumasi.manager", kumasi, false, "BRANCH_MANAGER");
        String accountId = funded("500.00");
        JsonNode approval = api.postIdempotent(WITHDRAWALS, teller.token(), key(), withdrawal(accountId, "200.00"))
                .expect(202).data().get("approval");
        String approvalId = approval.get("id").asString();

        api.get("/api/v1/approvals/" + approvalId, kumasiManager.token()).expectError(404, "RESOURCE_NOT_FOUND");
        assertThat(api.get("/api/v1/approvals", kumasiManager.token()).expect(200).data().get("items")).isEmpty();
        api.post(decide(approvalId, "approve"), kumasiManager.token(), decision(approval))
                .expectError(404, "RESOURCE_NOT_FOUND");
        assertThat(balance(accountId)).isEqualTo("500.00");
    }

    @Test
    void onlyAdministratorsSetThresholdsAndOnlyForMovements() {
        api.put("/api/v1/approval-policies/CASH_WITHDRAWAL/GHS", manager.token(), Map.of("thresholdAmount",
                "100.00", "active", true)).expectError(403, "ACCESS_DENIED");
        api.put("/api/v1/approval-policies/MANUAL_JOURNAL/GHS", tenant.adminToken(), Map.of("thresholdAmount",
                "100.00", "active", true)).expect(400);
        api.put("/api/v1/approval-policies/TRANSFER/XYZ", tenant.adminToken(), Map.of("thresholdAmount",
                "100.00", "active", true)).expectError(422, "CURRENCY_NOT_SUPPORTED");
        threshold("TRANSFER", "100.00");
        JsonNode changed = api.put("/api/v1/approval-policies/TRANSFER/GHS", tenant.adminToken(), Map.of(
                "thresholdAmount", "250.00", "active", false)).expect(200).data();
        assertThat(changed.get("thresholdAmount").asString()).isEqualTo("250.00");
        assertThat(api.get("/api/v1/approval-policies", manager.token()).expect(200).data()).hasSize(1);

        String from = funded("1000.00");
        String to = fixtures.openAccount(manager.token(), customer, current).get("id").asString();
        api.postIdempotent(TRANSFERS, teller.token(), key(), Map.of("fromAccountId", from, "toAccountId", to,
                "amount", "300.00")).expect(201);
    }

    @Test
    void theDatabaseRefusesToChangeADecidedRequest() {
        threshold("CASH_WITHDRAWAL", "100.00");
        String accountId = funded("500.00");
        JsonNode approval = api.postIdempotent(WITHDRAWALS, teller.token(), key(), withdrawal(accountId, "200.00"))
                .expect(202).data().get("approval");
        UUID approvalId = UUID.fromString(approval.get("id").asString());

        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql(
                        "UPDATE core.approval_request SET status = 'APPROVED', decided_at = now(), decided_by ="
                                + " requested_by WHERE id = :id")
                .param("id", approvalId).update()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_approval_request_four_eyes");
        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql(
                        "UPDATE core.approval_request SET amount = 1 WHERE id = :id")
                .param("id", approvalId).update()))
                .isInstanceOf(DataAccessException.class);

        api.post(decide(approvalId.toString(), "reject"), manager.token(), Map.of("note", "No",
                "version", approval.get("version").asLong())).expect(200);
        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql(
                        "UPDATE core.approval_request SET status = 'PENDING', decided_at = NULL WHERE id = :id")
                .param("id", approvalId).update()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql(
                        "DELETE FROM core.approval_request WHERE id = :id")
                .param("id", approvalId).update()))
                .isInstanceOf(DataAccessException.class);
    }

    // ---------------------------------------------------------------------------------------------------------

    private void threshold(String type, String amount) {
        api.put("/api/v1/approval-policies/" + type + "/GHS", tenant.adminToken(), Map.of("thresholdAmount", amount,
                "active", true)).expect(200);
    }

    private String funded(String amount) {
        String accountId = fixtures.openAccount(manager.token(), customer, current).get("id").asString();
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId, "amount", amount))
                .expect(201);
        return accountId;
    }

    private String balance(String accountId) {
        return api.get("/api/v1/accounts/" + accountId, manager.token()).expect(200).data().get("ledgerBalance")
                .asString();
    }

    private String gl(String code) {
        return inTenant(tenant.id(), () -> chartOfAccounts.list()).stream()
                .filter(account -> account.code().equals(code))
                .map(ChartOfAccountResponse::id)
                .findFirst()
                .orElseThrow()
                .toString();
    }

    private BigDecimal creditBalance(String code) {
        return inTenant(tenant.id(), () -> ledgerQueries.trialBalance(null, "GHS", null)).rows().stream()
                .filter(row -> row.code().equals(code))
                .map(TrialBalanceRow::creditBalance)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal feeIncome() {
        String feeGl = inTenant(tenant.id(), () -> chartOfAccounts.requireSystem(
                SystemAccount.ACCOUNT_FEE_INCOME).code());
        return creditBalance(feeGl);
    }

    private Map<String, Object> journal(String debitGl, String debit, String creditGl, String credit) {
        return Map.of("branchId", tenant.headOfficeId().toString(), "description", "Capital injection",
                "lines", List.of(
                        Map.of("chartOfAccountId", debitGl, "currency", "GHS", "direction", "DEBIT", "amount", debit),
                        Map.of("chartOfAccountId", creditGl, "currency", "GHS", "direction", "CREDIT",
                                "amount", credit)));
    }

    private long auditCount(String action, String resourceId) {
        return inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT count(*) FROM core.audit_log WHERE action = :action AND resource_id = :resourceId")
                .param("action", action)
                .param("resourceId", resourceId)
                .query(Long.class)
                .single());
    }

    private static Map<String, Object> withdrawal(String accountId, String amount) {
        return Map.of("accountId", accountId, "amount", amount);
    }

    private static Map<String, Object> decision(JsonNode approval) {
        return Map.of("note", "Checked", "version", approval.get("version").asLong());
    }

    private static String decide(String approvalId, String action) {
        return "/api/v1/approvals/" + approvalId + "/" + action;
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
