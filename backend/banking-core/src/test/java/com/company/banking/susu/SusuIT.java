package com.company.banking.susu;

import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Susu plans end to end: a plan schedules its cycle, field collections pay the oldest unpaid contributions (whole
 * contributions only, never more than scheduled), end-of-day marks the unpaid as missed, charges each cycle's
 * commission on what was paid, opens the next cycle and completes the plan at its end date.
 */
class SusuIT extends LedgerIntegrationTest {

    /** A Monday. */
    private static final LocalDate START = LocalDate.of(2027, 3, 1);

    @Autowired
    private BusinessDateService businessDates;

    private TenantHandle tenant;
    private StaffHandle manager;
    private StaffHandle officer;
    private String customerId;
    private String accountId;
    private String deviceId;
    private long sequence;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        StaffHandle lender = fixtures.createStaff(tenant, "lender", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        officer = fixtures.createStaff(tenant, "collector", tenant.headOfficeId(), false, "FIELD_OFFICER");
        customerId = fixtures.verifiedIndividual(lender, manager, tenant.headOfficeId(), "Adwoa").toString();
        Map<String, Object> susuTerms = terms("0");
        susuTerms.put("interestRate", "0");
        accountId = fixtures.openAccount(manager.token(), UUID.fromString(customerId),
                fixtures.publishedProduct(tenant, "SUSU01", "SUSU", susuTerms)).get("id").asString();
        inTenant(tenant.id(), () -> businessDates.roll(businessDates.today(), START));

        api.post("/api/v1/field/officers", manager.token(), Map.of("staffId", officer.id().toString(),
                "maxOfflineAmount", "5000.00", "maxOfflineHours", 72)).expect(201);
        api.post("/api/v1/field/assignments", manager.token(), Map.of("customerId", customerId,
                "officerId", officer.id().toString())).expect(201);
        deviceId = api.post("/api/v1/field/devices", officer.token(), Map.of("deviceKey", "phone-" + officer.id(),
                "name", "Tecno")).expect(200).data().get("id").asString();
    }

    @Test
    void aPlanRunsItsCyclesChargesCommissionAndCompletes() {
        assertThat(api.get("/api/v1/susu/frequencies", manager.token()).expect(200).data())
                .extracting(frequency -> frequency.get("code").asString())
                .containsExactly("DAILY", "MONTHLY", "WEEKLY");
        JsonNode opened = api.post("/api/v1/susu/plans", manager.token(), plan("2027-03-06")).expect(201).data();
        String planId = opened.at("/plan/id").asString();
        assertThat(opened.get("contributions")).extracting(c -> c.get("dueDate").asString())
                .containsExactly("2027-03-01", "2027-03-02", "2027-03-03");
        api.post("/api/v1/susu/plans", manager.token(), plan(null)).expectError(409, "PLAN_ALREADY_RUNNING");

        JsonNode mine = api.get("/api/v1/field/me/customers", officer.token()).expect(200).data();
        assertThat(mine.at("/0/susuPlans/0/planId").asString()).isEqualTo(planId);
        assertThat(mine.at("/0/susuPlans/0/unpaidScheduled").asInt()).isEqualTo(3);

        assertThat(collect(planId, "20.00").get("status").asString()).isEqualTo("POSTED");
        JsonNode notWhole = collect(planId, "15.00");
        assertThat(notWhole.get("code").asString()).isEqualTo("AMOUNT_NOT_WHOLE_CONTRIBUTIONS");
        assertThat(collect(planId, "20.00").get("code").asString()).as("only one more is scheduled")
                .isEqualTo("OVERPAID");
        assertThat(balance()).as("refused payments were rolled back").isEqualTo("20.00");

        endOfDay("2027-03-01");
        endOfDay("2027-03-02");
        endOfDay("2027-03-03"); // the 3rd is missed; cycle 1 closes: commission 10.00 of 20.00 paid
        assertThat(balance()).isEqualTo("10.00");
        assertThat(statuses(planId)).containsExactly("PAID", "PAID", "MISSED", "EXPECTED", "EXPECTED", "EXPECTED");

        assertThat(collect(planId, "20.00").get("status").asString()).as("late for the 3rd, early for the 4th")
                .isEqualTo("POSTED");
        endOfDay("2027-03-04");
        endOfDay("2027-03-05"); // Friday: the 5th is missed, the 6th not due yet
        endOfDay("2027-03-08"); // the 6th is missed; cycle 2 closes (1 paid: 10.00) and the plan ends

        JsonNode detail = api.get("/api/v1/susu/plans/" + planId, manager.token()).expect(200).data();
        assertThat(detail.at("/plan/status").asString()).isEqualTo("COMPLETED");
        assertThat(statuses(planId)).containsExactly("PAID", "PAID", "PAID", "PAID", "MISSED", "MISSED");
        assertThat(detail.get("commissions")).extracting(c -> c.get("amountCharged").asString())
                .containsExactly("10.00", "10.00");
        assertThat(detail.at("/plan/missed").asLong()).isEqualTo(2);
        assertThat(detail.at("/plan/arrears").asString()).isEqualTo("20.00");
        assertThat(balance()).isEqualTo("20.00");

        api.post("/api/v1/susu/plans/" + planId + "/contributions/5/waive", manager.token(), Map.of(
                "reason", "Customer was in hospital")).expect(200);
        api.post("/api/v1/susu/plans/" + planId + "/contributions/1/waive", manager.token(), Map.of(
                "reason", "Already paid")).expectError(422, "CONTRIBUTION_NOT_UNPAID");
        assertThat(statuses(planId).get(4)).isEqualTo("WAIVED");
        assertThat(collect(planId, "10.00").get("code").asString()).isEqualTo("PLAN_NOT_ACTIVE");
    }

    @Test
    void commissionIsNeverMoreThanWasPaidOrTheAccountHolds() {
        JsonNode opened = api.post("/api/v1/susu/plans", manager.token(), plan(null)).expect(201).data();
        String planId = opened.at("/plan/id").asString();
        endOfDay("2027-03-01");
        endOfDay("2027-03-02");
        endOfDay("2027-03-03"); // nothing paid: commission due 0

        JsonNode detail = api.get("/api/v1/susu/plans/" + planId, manager.token()).expect(200).data();
        assertThat(detail.at("/commissions/0/amountDue").asString()).isEqualTo("0.00");
        assertThat(detail.at("/plan/currentCycle").asInt()).isEqualTo(2);
        assertThat(detail.at("/plan/status").asString()).as("open-ended plans keep going").isEqualTo("ACTIVE");
        assertThat(balance()).isEqualTo("0.00");

        JsonNode version = api.get("/api/v1/susu/plans/" + planId, manager.token()).expect(200).data();
        api.post("/api/v1/susu/plans/" + planId + "/cancel", manager.token(), Map.of("reason", "Customer moved",
                "version", version.at("/plan/version").asLong())).expect(200);
        api.post("/api/v1/susu/plans/" + planId + "/cancel", officer.token(), Map.of("reason", "x",
                "version", 0)).expectError(403, "ACCESS_DENIED");
    }

    @Test
    void plansRunOnlyOnSusuAccounts() {
        String savings = fixtures.openAccount(manager.token(), UUID.fromString(customerId),
                fixtures.publishedProduct(tenant, "SAV01", "SAVINGS", terms("0"))).get("id").asString();
        Map<String, Object> request = plan(null);
        request.put("accountId", savings);
        api.post("/api/v1/susu/plans", manager.token(), request).expectError(422, "NOT_A_SUSU_ACCOUNT");
        Map<String, Object> past = plan(null);
        past.put("startDate", "2027-02-26");
        api.post("/api/v1/susu/plans", manager.token(), past).expectError(422, "INVALID_PLAN");
        Map<String, Object> greedy = plan(null);
        greedy.put("commissionContributions", 3);
        api.post("/api/v1/susu/plans", manager.token(), greedy).expectError(422, "INVALID_PLAN");
    }

    // ---------------------------------------------------------------------------------------------------------

    private Map<String, Object> plan(String endDate) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("customerId", customerId);
        request.put("accountId", accountId);
        request.put("frequencyCode", "DAILY");
        request.put("contributionAmount", "10.00");
        request.put("cycleLength", 3);
        request.put("commissionContributions", 1);
        request.put("startDate", START.toString());
        if (endDate != null) {
            request.put("endDate", endDate);
        }
        return request;
    }

    private JsonNode collect(String planId, String amount) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("clientReference", UUID.randomUUID().toString());
        item.put("sequenceNo", ++sequence);
        item.put("customerId", customerId);
        item.put("accountId", accountId);
        item.put("susuPlanId", planId);
        item.put("amount", amount);
        item.put("currency", "GHS");
        item.put("collectedAt", Instant.now().minus(1, ChronoUnit.MINUTES).toString());
        return api.post("/api/v1/field/sync", officer.token(), Map.of("deviceId", deviceId, "collections",
                List.of(item))).expect(200).data().at("/collections/0");
    }

    private void endOfDay(String expectedDate) {
        JsonNode run = api.post("/api/v1/operations/eod", tenant.adminToken(), null).expect(202).data();
        assertThat(run.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(run.get("businessDate").asString()).isEqualTo(expectedDate);
    }

    private List<String> statuses(String planId) {
        List<String> statuses = new ArrayList<>();
        api.get("/api/v1/susu/plans/" + planId, manager.token()).expect(200).data().get("contributions")
                .forEach(contribution -> statuses.add(contribution.get("status").asString()));
        return statuses;
    }

    private String balance() {
        return api.get("/api/v1/accounts/" + accountId, manager.token()).expect(200).data().get("ledgerBalance")
                .asString();
    }
}
