package com.company.banking.account;

import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * Statements come from the ledger: every entry (charges included) with a running balance that ends at the
 * account's balance.
 */
class AccountStatementIT extends LedgerIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    private TenantHandle tenant;
    private StaffHandle officer;
    private StaffHandle manager;
    private StaffHandle teller;
    private String accountId;
    private String accountNumber;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        UUID customer = fixtures.verifiedIndividual(officer, manager, tenant.headOfficeId(), "Ama");
        Map<String, Object> charged = terms("0");
        charged.put("charges", List.of(Map.of("event", "CASH_WITHDRAWAL", "name", "Withdrawal fee",
                "calculation", "FLAT", "flatAmount", "1.50")));
        JsonNode account = fixtures.openAccount(manager.token(), customer,
                fixtures.publishedProduct(tenant, "SAVE01", "SAVINGS", charged));
        accountId = account.get("id").asString();
        accountNumber = account.get("accountNumber").asString();
        String other = fixtures.openAccount(manager.token(), customer,
                fixtures.publishedProduct(tenant, "CURR01", "CURRENT", terms("0"))).get("id").asString();
        fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");

        post("/api/v1/transactions/deposits", Map.of("accountId", accountId, "amount", "500.00"));
        post("/api/v1/transactions/withdrawals", Map.of("accountId", accountId, "amount", "100.00",
                "narration", "School fees"));
        post("/api/v1/transactions/deposits", Map.of("accountId", other, "amount", "80.00"));
        post("/api/v1/transactions/transfers", Map.of("fromAccountId", other, "toAccountId", accountId,
                "amount", "50.00"));
    }

    @Test
    void theStatementListsEveryEntryWithARunningBalanceEndingAtTheAccountBalance() {
        JsonNode statement = api.get(statementPath(), teller.token()).expect(200).data();

        assertThat(statement.get("accountNumber").asString()).isEqualTo(accountNumber);
        assertThat(statement.get("holders").get(0).asString()).isEqualTo("Ama Mensah");
        assertThat(statement.get("openingBalance").asString()).isEqualTo("0.00");
        JsonNode lines = statement.get("lines");
        assertThat(lines).hasSize(4);
        assertThat(lines.get(0).get("credit").asString()).isEqualTo("500.00");
        assertThat(lines.get(0).get("balance").asString()).isEqualTo("500.00");
        assertThat(lines.get(1).get("debit").asString()).isEqualTo("100.00");
        assertThat(lines.get(1).get("description").asString()).isEqualTo("School fees");
        assertThat(lines.get(2).get("debit").asString()).isEqualTo("1.50");
        assertThat(lines.get(2).get("description").asString()).isEqualTo("Withdrawal fee");
        assertThat(lines.get(2).get("reference").asString()).as("same transaction")
                .isEqualTo(lines.get(1).get("reference").asString());
        assertThat(lines.get(3).get("credit").asString()).isEqualTo("50.00");
        assertThat(lines.get(3).get("reference").asString()).startsWith("TXN-");
        assertThat(statement.get("totalDebits").asString()).isEqualTo("101.50");
        assertThat(statement.get("totalCredits").asString()).isEqualTo("550.00");
        assertThat(statement.get("closingBalance").asString()).isEqualTo("448.50");
        assertThat(api.get("/api/v1/accounts/" + accountId, teller.token()).expect(200).data().get("ledgerBalance")
                .asString()).isEqualTo("448.50");

        LocalDate tomorrow = today().plusDays(1);
        JsonNode later = api.get(statementPath() + "?from=" + tomorrow + "&to=" + tomorrow, teller.token())
                .expect(200).data();
        assertThat(later.get("openingBalance").asString()).isEqualTo("448.50");
        assertThat(later.get("lines")).isEmpty();
        assertThat(later.get("closingBalance").asString()).isEqualTo("448.50");
    }

    @Test
    void statementsDownloadAsPdfOrCsvAndEveryDownloadIsAudited() {
        byte[] pdf = api.download(statementPath() + "/download?format=PDF", teller.token());
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String csv = new String(api.download(statementPath() + "/download?format=CSV", teller.token()),
                StandardCharsets.UTF_8);
        assertThat(csv).contains(accountNumber, "\"Withdrawal fee\"", "\"Closing balance\",\"101.50\",\"550.00\","
                + "\"448.50\"");

        long downloads = inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT count(*) FROM core.audit_log WHERE action = 'ACCOUNT_STATEMENT_EXPORTED'"
                                + " AND resource_id = :accountId")
                .param("accountId", accountId)
                .query(Long.class)
                .single());
        assertThat(downloads).isEqualTo(2);
    }

    @Test
    void periodsAndAccessAreChecked() {
        LocalDate today = today();
        api.get(statementPath() + "?from=" + today + "&to=" + today.minusDays(1), teller.token())
                .expectError(400, "VALIDATION_FAILED");
        api.get(statementPath() + "?from=" + today.minusDays(400) + "&to=" + today, teller.token())
                .expectError(400, "VALIDATION_FAILED");
        api.get(statementPath() + "/download?format=XLS", teller.token()).expect(400);
        api.get(statementPath(), officer.token()).expectError(403, "ACCESS_DENIED");

        UUID kumasi = fixtures.createBranch(tenant, "KUM");
        StaffHandle kumasiManager = fixtures.createStaff(tenant, "kumasi.manager", kumasi, false, "BRANCH_MANAGER");
        api.get(statementPath(), kumasiManager.token()).expectError(404, "RESOURCE_NOT_FOUND");
        TenantHandle other = fixtures.onboardTenant();
        StaffHandle outsider = fixtures.createStaff(other, "teller", other.headOfficeId(), true, "TELLER");
        api.get(statementPath(), outsider.token()).expectError(404, "RESOURCE_NOT_FOUND");
    }

    private void post(String path, Map<String, Object> body) {
        api.postIdempotent(path, teller.token(), UUID.randomUUID().toString(), body).expect(201);
    }

    private LocalDate today() {
        return LocalDate.parse(api.get(statementPath(), teller.token()).expect(200).data().get("to").asString());
    }

    private String statementPath() {
        return "/api/v1/accounts/" + accountId + "/statement";
    }
}
