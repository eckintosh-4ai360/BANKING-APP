package com.company.banking.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.operations.service.BusinessCalendarService;
import com.company.banking.support.EndOfDayProbes;
import com.company.banking.support.EndOfDayProbes.StopProbe;
import com.company.banking.support.EndOfDayProbes.SwitchableCheck;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.ProductRequests;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * End-of-day: the date rolls, the steps run for the closed date, and a run stopped at any checkpoint resumes to
 * exactly the result of an uninterrupted run.
 */
@Import(EndOfDayProbes.class)
class EndOfDayIT extends LedgerIntegrationTest {

    @Autowired
    private StopProbe probe;

    @Autowired
    private SwitchableCheck check;

    @Autowired
    private BusinessCalendarService calendar;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private BusinessDateService businessDates;

    /** The gate closes a month end that falls on a Friday, so interest is accrued over the weekend and paid. */
    private static final LocalDate MONTH_END = LocalDate.of(2027, 4, 30);

    @AfterEach
    void disarm() {
        probe.armedStep = null;
        check.problem = null;
    }

    @Test
    void endOfDayRollsTheDateRunsItsStepsAndLaterPostingsCarryTheNewDate() {
        TenantHandle tenant = fixtures.onboardTenant();
        UUID account = openDepositAccount(tenant.id(), tenant.headOfficeId(), "GHS", "Ama");
        deposit(tenant.id(), account, tenant.headOfficeId(), "GHS", "250.00");
        String today = businessDate(tenant);

        JsonNode run = api.post("/api/v1/operations/eod", tenant.adminToken(), null).expect(202).data();
        assertThat(run.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(run.get("businessDate").asString()).isEqualTo(today);
        LocalDate next = inTenant(tenant.id(), () -> calendar.nextBusinessDate(LocalDate.parse(today)));
        assertThat(run.get("nextBusinessDate").asString()).isEqualTo(next.toString());
        assertThat(run.get("steps")).allSatisfy(step -> assertThat(step.get("status").asString()).isEqualTo("DONE"));
        assertThat(businessDate(tenant)).isEqualTo(next.toString());

        Map<String, List<BigDecimal>> firstDay = snapshot(tenant, today);
        assertThat(firstDay.get("1110")).containsExactly(money("0.0000"), money("250.0000"), money("0.0000"),
                money("250.0000"));
        assertThat(firstDay.get("2110")).containsExactly(money("0.0000"), money("0.0000"), money("250.0000"),
                money("-250.0000"));

        PostedJournal later = deposit(tenant.id(), account, tenant.headOfficeId(), "GHS", "40.00");
        assertThat(later.businessDate()).isEqualTo(next);

        api.post("/api/v1/operations/eod", tenant.adminToken(), null).expect(202);
        Map<String, List<BigDecimal>> secondDay = snapshot(tenant, next.toString());
        assertThat(secondDay.get("2110")).as("opening from the previous snapshot")
                .containsExactly(money("-250.0000"), money("0.0000"), money("40.0000"), money("-290.0000"));
        assertThat(api.get("/api/v1/operations/eod", tenant.adminToken()).expect(200).data().get("items")).hasSize(2);
    }

    @Test
    void aRunStoppedAtAnyCheckpointResumesToTheResultOfAnUninterruptedRun() {
        TenantHandle reference = scenario();
        api.post("/api/v1/operations/eod", reference.adminToken(), null).expect(202);
        assertThat(previousDate(reference)).isEqualTo(MONTH_END.toString());
        Map<String, List<BigDecimal>> expected = snapshot(reference, previousDate(reference));
        List<String> expectedInterest = interest(reference);
        String expectedCash = cash(reference);
        String expectedSusu = susu(reference);
        assertThat(expectedSusu).as("the one-day cycle closed and the next one opened")
                .isEqualTo("contributions 2 EXPECTED 1 MISSED 1, commissions 1, cycle 2");
        assertThat(expectedCash).isEqualTo("DRAWER NOT_COUNTED 1, VAULT NOT_COUNTED 1");
        assertThat(expectedInterest.get(0)).as("three accounts accrue three days").startsWith("accruals 9 ")
                .endsWith(" unposted 0");
        assertThat(expectedInterest.get(1)).as("and are paid at the month end").startsWith("payouts 3 ")
                .endsWith(" 3");

        String[][] checkpoints = {
                {"DEPOSIT_INTEREST_ACCRUAL", "batch"}, {"DEPOSIT_INTEREST_ACCRUAL", "journal"},
                {"DEPOSIT_INTEREST_PAYOUT", "batch"}, {"SUSU_CONTRIBUTIONS", "batch"}, {"DORMANCY", "batch"},
                {"CASH_RECONCILIATION", "written"},
                {"GL_SNAPSHOT", "before"}, {"GL_SNAPSHOT", "written"}, {"LEDGER_RECONCILIATION", "before"}};
        for (String[] checkpoint : checkpoints) {
            TenantHandle tenant = scenario();
            probe.armedStep = checkpoint[0];
            probe.armedPoint = checkpoint[1];

            JsonNode failed = api.post("/api/v1/operations/eod", tenant.adminToken(), null).expect(202).data();
            assertThat(failed.get("status").asString()).as("stopped at %s/%s", checkpoint[0], checkpoint[1])
                    .isEqualTo("FAILED");
            assertThat(failed.get("failedStep").asString()).isEqualTo(checkpoint[0]);
            api.post("/api/v1/operations/eod", tenant.adminToken(), null).expectError(409, "EOD_ALREADY_RUNNING");

            JsonNode resumed = api.post("/api/v1/operations/eod/" + failed.get("id").asString() + "/resume",
                    tenant.adminToken(), null).expect(202).data();
            assertThat(resumed.get("status").asString()).isEqualTo("COMPLETED");
            assertThat(resumed.get("attempts").asInt()).isEqualTo(2);
            assertThat(snapshot(tenant, previousDate(tenant))).as("after resuming from %s/%s", checkpoint[0],
                    checkpoint[1]).isEqualTo(expected);
            assertThat(interest(tenant)).as("interest after resuming from %s/%s", checkpoint[0], checkpoint[1])
                    .isEqualTo(expectedInterest);
            assertThat(cash(tenant)).as("cash positions after resuming from %s/%s", checkpoint[0], checkpoint[1])
                    .isEqualTo(expectedCash);
            assertThat(susu(tenant)).as("susu after resuming from %s/%s", checkpoint[0], checkpoint[1])
                    .isEqualTo(expectedSusu);
        }
    }

    @Test
    void checksMustPassBeforeTheDateCloses() {
        TenantHandle tenant = fixtures.onboardTenant();
        String today = businessDate(tenant);
        check.problem = "Teller session T1 is still open";

        api.post("/api/v1/operations/eod", tenant.adminToken(), null).expectError(422, "EOD_CHECKS_FAILED");
        assertThat(businessDate(tenant)).isEqualTo(today);

        StaffHandle manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        check.problem = null;
        api.post("/api/v1/operations/eod", manager.token(), null).expectError(403, "ACCESS_DENIED");
        api.get("/api/v1/operations/eod", manager.token()).expect(200);
    }

    // ---------------------------------------------------------------------------------------------------------

    /**
     * At {@link #MONTH_END}: two ledger accounts in two branches with a deposit and an inter-branch transfer, and
     * three customer accounts earning interest (daily balance at two rates, and minimum balance).
     */
    private TenantHandle scenario() {
        TenantHandle tenant = fixtures.onboardTenant();
        inTenant(tenant.id(), () -> businessDates.roll(businessDates.today(), MONTH_END));
        UUID kumasi = fixtures.createBranch(tenant, "KUM");
        UUID first = openDepositAccount(tenant.id(), tenant.headOfficeId(), "GHS", "First");
        UUID second = openDepositAccount(tenant.id(), kumasi, "GHS", "Second");
        deposit(tenant.id(), first, tenant.headOfficeId(), "GHS", "1000.00");
        transfer(tenant.id(), tenant.headOfficeId(), first, second, "125.50");
        withdraw(tenant.id(), second, kumasi, "GHS", "25.50");

        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        StaffHandle manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        UUID customer = fixtures.verifiedIndividual(officer, manager, tenant.headOfficeId(), "Esi");
        api.post("/api/v1/cash/vaults", manager.token(), Map.of("branchId", tenant.headOfficeId().toString(),
                "currency", "GHS", "name", "Main vault")).expect(201);
        api.post("/api/v1/cash/drawers", manager.token(), Map.of("branchId", tenant.headOfficeId().toString(),
                "currency", "GHS", "code", "T1", "name", "Till 1")).expect(201);
        Map<String, Object> minimum = ProductRequests.terms("0");
        minimum.put("interestCalcMethod", "MIN_MONTHLY_BALANCE");
        String daily = fixtures.publishedProduct(tenant, "SAVDAY", "SAVINGS", ProductRequests.terms("0"));
        String lowest = fixtures.publishedProduct(tenant, "SAVMIN", "SAVINGS", minimum);
        Map<String, Object> susuTerms = ProductRequests.terms("0");
        susuTerms.put("interestRate", "0");
        String susuAccount = fixtures.openAccount(manager.token(), customer,
                fixtures.publishedProduct(tenant, "SUSU01", "SUSU", susuTerms)).get("id").asString();
        api.post("/api/v1/susu/plans", manager.token(), Map.of("customerId", customer.toString(),
                "accountId", susuAccount, "frequencyCode", "DAILY", "contributionAmount", "5.00", "cycleLength", 1,
                "commissionContributions", 0)).expect(201);
        Map<String, String> funding = Map.of(daily, "1000.00", lowest, "777.77");
        for (String productId : List.of(daily, daily, lowest)) {
            String accountId = fixtures.openAccount(manager.token(), customer, productId).get("id").asString();
            deposit(tenant.id(), ledgerAccountOf(tenant, accountId), tenant.headOfficeId(), "GHS",
                    funding.get(productId));
        }
        return tenant;
    }

    private UUID ledgerAccountOf(TenantHandle tenant, String accountId) {
        return inTenant(tenant.id(), () -> jdbcClient.sql("SELECT ledger_account_id FROM core.account WHERE id = :id")
                .param("id", UUID.fromString(accountId))
                .query(UUID.class)
                .single());
    }

    /** The tenant's susu contributions by status, commissions and the plan's cycle. */
    private String susu(TenantHandle tenant) {
        return inTenant(tenant.id(), () -> String.join(", ",
                jdbcClient.sql("""
                                SELECT 'contributions ' || count(*) || ' ' || string_agg(status || ' ' || n, ' '
                                       ORDER BY status)
                                FROM (SELECT status, count(*) AS n FROM core.susu_contribution GROUP BY status) s,
                                     (SELECT count(*) FROM core.susu_contribution) total(count)
                                GROUP BY total.count""").query(String.class).single(),
                jdbcClient.sql("SELECT 'commissions ' || count(*) FROM core.susu_cycle_commission")
                        .query(String.class).single(),
                jdbcClient.sql("SELECT 'cycle ' || current_cycle FROM core.susu_plan").query(String.class).single()));
    }

    /** The tenant's cash positions: how many of each type and status. */
    private String cash(TenantHandle tenant) {
        return String.join(", ", inTenant(tenant.id(), () -> jdbcClient.sql("""
                        SELECT cash_point_type || ' ' || status || ' ' || count(*) FROM core.cash_position
                        GROUP BY cash_point_type, status ORDER BY cash_point_type, status""")
                .query(String.class).list()));
    }

    /** Interest rows of the tenant: how many, and their totals. */
    private List<String> interest(TenantHandle tenant) {
        return inTenant(tenant.id(), () -> List.of(
                jdbcClient.sql("""
                                SELECT 'accruals ' || count(*) || ' ' || coalesce(sum(amount), 0) || ' '
                                       || coalesce(sum(gl_amount), 0) || ' unposted '
                                       || count(*) FILTER (WHERE gl_amount > 0 AND journal_entry_id IS NULL)
                                FROM core.deposit_interest_accrual""").query(String.class).single(),
                jdbcClient.sql("""
                                SELECT 'payouts ' || count(*) || ' ' || coalesce(sum(amount), 0) || ' '
                                       || count(journal_entry_id)
                                FROM core.deposit_interest_payout""").query(String.class).single(),
                jdbcClient.sql("""
                                SELECT 'positions ' || count(*) || ' ' || coalesce(sum(accrued_exact), 0) || ' '
                                       || coalesce(sum(paid_out), 0) || ' ' || min(period_start)
                                FROM core.deposit_interest_position""").query(String.class).single()));
    }

    /**
     * The day's snapshot by GL code (summed over branches): opening, debits, credits, closing.
     */
    private Map<String, List<BigDecimal>> snapshot(TenantHandle tenant, String date) {
        Map<String, List<BigDecimal>> rows = new TreeMap<>();
        inTenant(tenant.id(), () -> jdbcClient.sql("""
                        SELECT c.code, sum(s.opening_balance) AS opening, sum(s.debits) AS debits,
                               sum(s.credits) AS credits, sum(s.closing_balance) AS closing
                        FROM core.gl_balance_snapshot s
                        JOIN core.chart_of_account c ON c.id = s.chart_of_account_id
                        WHERE s.business_date = :date GROUP BY c.code""")
                .param("date", LocalDate.parse(date))
                .query((rs, rowNum) -> rows.put(rs.getString("code"), List.of(rs.getBigDecimal("opening"),
                        rs.getBigDecimal("debits"), rs.getBigDecimal("credits"), rs.getBigDecimal("closing"))))
                .list());
        return rows;
    }

    private String businessDate(TenantHandle tenant) {
        return api.get("/api/v1/operations/business-date", tenant.adminToken()).expect(200).data().get("businessDate")
                .asString();
    }

    private String previousDate(TenantHandle tenant) {
        return api.get("/api/v1/operations/business-date", tenant.adminToken()).expect(200).data()
                .get("previousBusinessDate").asString();
    }
}
