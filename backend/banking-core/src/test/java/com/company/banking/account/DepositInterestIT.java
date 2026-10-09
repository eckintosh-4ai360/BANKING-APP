package com.company.banking.account;

import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import java.math.BigDecimal;
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
 * Deposit interest and dormancy through end-of-day: daily accruals reach the GL without drift, interest is paid at
 * the month end (including the weekend after it), the minimum-balance method pays on the lowest balance, and idle
 * accounts turn dormant after the product's dormancy period.
 */
class DepositInterestIT extends LedgerIntegrationTest {

    private static final String DEPOSITS = "/api/v1/transactions/deposits";
    private static final String TRANSFERS = "/api/v1/transactions/transfers";
    /** A Tuesday; April 2027 ends on a Friday, so its last end-of-day also closes the weekend. */
    private static final LocalDate START = LocalDate.of(2027, 4, 27);

    @Autowired
    private BusinessDateService businessDates;

    @Autowired
    private JdbcClient jdbcClient;

    private TenantHandle tenant;
    private StaffHandle manager;
    private StaffHandle teller;
    private UUID customer;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        customer = fixtures.verifiedIndividual(officer, manager, tenant.headOfficeId(), "Ama");
        inTenant(tenant.id(), () -> businessDates.roll(businessDates.today(), START));
    }

    @Test
    void dailyInterestAccruesWithoutDriftAndIsPaidAtTheMonthEnd() {
        // 10,000 at 3.65% on actual/365 earns exactly 1.00 a day.
        String daily = account(fixtures.publishedProduct(tenant, "SAVDAY", "SAVINGS", interest("3.65",
                "DAILY_BALANCE")));
        String minimum = account(fixtures.publishedProduct(tenant, "SAVMIN", "SAVINGS", interest("12",
                "MIN_MONTHLY_BALANCE")));
        String current = account(fixtures.publishedProduct(tenant, "CURR01", "CURRENT", noInterest()));
        JsonNode till = fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");
        deposit(daily, "10000.00");
        deposit(minimum, "1000.00");
        deposit(current, "500.00");
        closeTill(till, Map.of("200", 57, "100", 1));

        assertThat(endOfDay()).isEqualTo("2027-04-27");
        transfer(minimum, current, "400.00");
        assertThat(endOfDay()).isEqualTo("2027-04-28");
        assertThat(endOfDay()).isEqualTo("2027-04-29");
        JsonNode monthEnd = endOfDayRun();
        assertThat(monthEnd.get("businessDate").asString()).isEqualTo("2027-04-30");
        assertThat(monthEnd.get("nextBusinessDate").asString()).isEqualTo("2027-05-03");

        // April 27-30 earned 4.00, credited on the 30th; the weekend after it earns on 10,004.00.
        assertThat(balance(daily)).isEqualTo("10004.00");
        assertThat(accruals(daily)).containsExactly(
                accrual("2027-04-27", "10000.0000", "1.00000000", "1.0000"),
                accrual("2027-04-28", "10000.0000", "1.00000000", "1.0000"),
                accrual("2027-04-29", "10000.0000", "1.00000000", "1.0000"),
                accrual("2027-04-30", "10000.0000", "1.00000000", "1.0000"),
                accrual("2027-05-01", "10004.0000", "1.00040000", "1.0000"),
                accrual("2027-05-02", "10004.0000", "1.00040000", "1.0000"));
        assertThat(payouts(daily)).containsExactly(List.of("2027-04-27", "2027-04-30", "4.0000", "4.00000000"));

        // The lowest balance (600.00 from the 28th) for the 4 days: 600 x 12% x 4 / 365 = 0.78904110.
        assertThat(balance(minimum)).isEqualTo("600.79");
        assertThat(payouts(minimum)).containsExactly(List.of("2027-04-27", "2027-04-30", "0.7900", "0.78904110"));
        assertThat(accruals(minimum).get(4)).as("the weekend earns on the credited interest")
                .isEqualTo(accrual("2027-05-01", "600.7900", "0.19752000", "0.0000"));
        assertThat(accruals(current)).isEmpty();

        // Interest payable holds what was accrued and not yet paid: round(6.0008) - 4.00.
        assertThat(closingBalance("2210", "2027-04-30")).isEqualByComparingTo("-2.00");
        assertThat(closingBalance("5100", "2027-04-30")).isEqualByComparingTo("6.79");

        assertThat(endOfDay()).isEqualTo("2027-05-03");
        assertThat(accruals(daily).getLast()).isEqualTo(accrual("2027-05-03", "10004.0000", "1.00040000", "1.0000"));
        assertThat(closingBalance("2210", "2027-05-03")).isEqualByComparingTo("-3.00");
        assertThat(position(daily)).containsExactly("7.00120000", "4.00000000", "4.0000", "2027-05-01");

        JsonNode statement = api.get("/api/v1/accounts/" + daily + "/statement?from=2027-04-01&to=2027-05-31",
                manager.token()).expect(200).data();
        assertThat(statement.get("lines")).anySatisfy(line -> {
            assertThat(line.get("date").asString()).isEqualTo("2027-04-30");
            assertThat(line.get("description").asString()).isEqualTo("Interest 2027-04-27 to 2027-04-30");
            assertThat(new BigDecimal(line.get("credit").asString())).isEqualByComparingTo("4.00");
        });
    }

    @Test
    void accountsWithoutCustomerActivityForTheDormancyPeriodTurnDormant() {
        Map<String, Object> thirtyDays = noInterest();
        thirtyDays.put("dormancyDays", 30);
        String product = fixtures.publishedProduct(tenant, "SAV30", "SAVINGS", thirtyDays);
        String idle = account(product);
        String busy = account(product);
        String other = account(fixtures.publishedProduct(tenant, "CURR01", "CURRENT", noInterest()));
        JsonNode till = fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");
        deposit(idle, "50.00");
        deposit(busy, "50.00");
        deposit(other, "50.00");
        closeTill(till, Map.of("50", 3));
        assertThat(endOfDay()).isEqualTo("2027-04-27");

        // Skip ahead to the 30th day without activity.
        inTenant(tenant.id(), () -> businessDates.roll(LocalDate.of(2027, 4, 28), LocalDate.of(2027, 5, 27)));
        transfer(busy, other, "5.00");
        assertThat(endOfDay()).isEqualTo("2027-05-27");

        assertThat(status(idle)).isEqualTo("DORMANT");
        assertThat(status(busy)).isEqualTo("ACTIVE");
        assertThat(status(other)).isEqualTo("ACTIVE");
        assertThat(inTenant(tenant.id(), () -> jdbcClient.sql("""
                        SELECT count(*) FROM core.audit_log
                        WHERE action = 'ACCOUNT_DORMANT' AND resource_id = :id""")
                .param("id", idle)
                .query(Long.class)
                .single())).isEqualTo(1L);

        // A dormant account still accepts money but pays nothing out.
        transfer(other, idle, "1.00");
        api.postIdempotent(TRANSFERS, teller.token(), key(), Map.of("fromAccountId", idle, "toAccountId", other,
                "amount", "1.00")).expectError(422, "ACCOUNT_NOT_DEBITABLE");
        assertThat(balance(idle)).isEqualTo("51.00");
    }

    // ---------------------------------------------------------------------------------------------------------

    private static Map<String, Object> interest(String rate, String method) {
        Map<String, Object> terms = terms("0");
        terms.put("interestRate", rate);
        terms.put("interestCalcMethod", method);
        terms.put("interestPostingFrequency", "MONTHLY");
        terms.put("dayCount", "ACTUAL_365F");
        return terms;
    }

    private static Map<String, Object> noInterest() {
        Map<String, Object> terms = terms("0");
        terms.put("interestRate", "0");
        return terms;
    }

    private String account(String productId) {
        return fixtures.openAccount(manager.token(), customer, productId).get("id").asString();
    }

    private void deposit(String accountId, String amount) {
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId, "amount", amount))
                .expect(201);
    }

    private void transfer(String from, String to, String amount) {
        api.postIdempotent(TRANSFERS, teller.token(), key(), Map.of("fromAccountId", from, "toAccountId", to,
                "amount", amount)).expect(201);
    }

    private void closeTill(JsonNode session, Map<String, Integer> denominations) {
        JsonNode closed = api.post("/api/v1/teller/sessions/" + session.get("id").asString() + "/close",
                teller.token(), Map.of("count", Map.of("denominations", denominations),
                        "version", session.get("version").asLong())).expect(200).data();
        assertThat(closed.get("status").asString()).isEqualTo("CLOSED");
    }

    private JsonNode endOfDayRun() {
        JsonNode run = api.post("/api/v1/operations/eod", tenant.adminToken(), null).expect(202).data();
        assertThat(run.get("status").asString()).isEqualTo("COMPLETED");
        return run;
    }

    /** Runs end-of-day and returns the date it closed. */
    private String endOfDay() {
        return endOfDayRun().get("businessDate").asString();
    }

    private String balance(String accountId) {
        return api.get("/api/v1/accounts/" + accountId, manager.token()).expect(200).data().get("ledgerBalance")
                .asString();
    }

    private String status(String accountId) {
        return api.get("/api/v1/accounts/" + accountId, manager.token()).expect(200).data().get("status").asString();
    }

    private static List<String> accrual(String date, String balance, String amount, String glAmount) {
        return List.of(date, balance, amount, glAmount);
    }

    /** Date, balance, exact interest and GL amount of each accrued day. */
    private List<List<String>> accruals(String accountId) {
        return inTenant(tenant.id(), () -> jdbcClient.sql("""
                        SELECT accrual_date, balance, amount, gl_amount FROM core.deposit_interest_accrual
                        WHERE account_id = :id ORDER BY accrual_date""")
                .param("id", UUID.fromString(accountId))
                .query((rs, rowNum) -> List.of(rs.getString(1), rs.getBigDecimal(2).toPlainString(),
                        rs.getBigDecimal(3).toPlainString(), rs.getBigDecimal(4).toPlainString()))
                .list());
    }

    private List<List<String>> payouts(String accountId) {
        return inTenant(tenant.id(), () -> jdbcClient.sql("""
                        SELECT period_start, period_end, amount, exact_amount FROM core.deposit_interest_payout
                        WHERE account_id = :id ORDER BY period_end""")
                .param("id", UUID.fromString(accountId))
                .query((rs, rowNum) -> List.of(rs.getString(1), rs.getString(2), rs.getBigDecimal(3).toPlainString(),
                        rs.getBigDecimal(4).toPlainString()))
                .list());
    }

    private List<String> position(String accountId) {
        return inTenant(tenant.id(), () -> jdbcClient.sql("""
                        SELECT accrued_exact, settled_exact, paid_out, period_start
                        FROM core.deposit_interest_position WHERE account_id = :id""")
                .param("id", UUID.fromString(accountId))
                .query((rs, rowNum) -> List.of(rs.getBigDecimal(1).toPlainString(),
                        rs.getBigDecimal(2).toPlainString(), rs.getBigDecimal(3).toPlainString(), rs.getString(4)))
                .single());
    }

    /** A GL's closing balance on a date, debit-positive, summed over branches. */
    private BigDecimal closingBalance(String glCode, String date) {
        return inTenant(tenant.id(), () -> jdbcClient.sql("""
                        SELECT coalesce(sum(s.closing_balance), 0) FROM core.gl_balance_snapshot s
                        JOIN core.chart_of_account c ON c.id = s.chart_of_account_id
                        WHERE c.code = :code AND s.business_date = :date""")
                .param("code", glCode)
                .param("date", LocalDate.parse(date))
                .query(BigDecimal.class)
                .single());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
