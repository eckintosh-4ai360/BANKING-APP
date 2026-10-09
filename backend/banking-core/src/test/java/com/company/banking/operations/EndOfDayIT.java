package com.company.banking.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.common.eod.EndOfDayCheck;
import com.company.banking.common.eod.EndOfDayProbe;
import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.operations.service.BusinessCalendarService;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * End-of-day: the date rolls, the steps run for the closed date, and a run stopped at any checkpoint resumes to
 * exactly the result of an uninterrupted run.
 */
@Import(EndOfDayIT.Probes.class)
class EndOfDayIT extends LedgerIntegrationTest {

    /** Stops a run (as a crash would) the first time the armed checkpoint is reached. */
    static class StopProbe implements EndOfDayProbe {
        volatile String armedStep;
        volatile String armedPoint;

        @Override
        public void reached(String step, String point) {
            if (step.equals(armedStep) && point.equals(armedPoint)) {
                armedStep = null;
                throw new IllegalStateException("Simulated crash at " + step + "/" + point);
            }
        }
    }

    static class SwitchableCheck implements EndOfDayCheck {
        volatile String problem;

        @Override
        public List<String> problems(LocalDate businessDate) {
            return problem == null ? List.of() : List.of(problem);
        }
    }

    @TestConfiguration
    static class Probes {
        @Bean
        StopProbe stopProbe() {
            return new StopProbe();
        }

        @Bean
        SwitchableCheck switchableCheck() {
            return new SwitchableCheck();
        }
    }

    @Autowired
    private StopProbe probe;

    @Autowired
    private SwitchableCheck check;

    @Autowired
    private BusinessCalendarService calendar;

    @Autowired
    private JdbcClient jdbcClient;

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
        Map<String, List<BigDecimal>> expected = snapshot(reference, previousDate(reference));

        String[][] checkpoints = {
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

    /** Two accounts in two branches with a deposit and an inter-branch transfer. */
    private TenantHandle scenario() {
        TenantHandle tenant = fixtures.onboardTenant();
        UUID kumasi = fixtures.createBranch(tenant, "KUM");
        UUID first = openDepositAccount(tenant.id(), tenant.headOfficeId(), "GHS", "First");
        UUID second = openDepositAccount(tenant.id(), kumasi, "GHS", "Second");
        deposit(tenant.id(), first, tenant.headOfficeId(), "GHS", "1000.00");
        transfer(tenant.id(), tenant.headOfficeId(), first, second, "125.50");
        withdraw(tenant.id(), second, kumasi, "GHS", "25.50");
        return tenant;
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
