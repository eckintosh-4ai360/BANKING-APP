package com.company.banking.loan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Collections and the end of a loan's troubles: the arrears queue, calls and promises to pay (kept or broken at
 * end-of-day), a restructure that changes the schedule but not what is owed today and holds the delinquency band, and
 * a write-off against the provision followed by recoveries. Restructures and write-offs need a second person.
 */
class LoanCollectionsIT extends LoanIntegrationTest {

    @BeforeEach
    void setUpProduct() {
        productId = publishedLoanProduct("COL01", loanTerms());
    }

    @Test
    void theArrearsQueueAndPromisesToPay() {
        // 1,000 flat at 24%: two installments of 500 + 20, due 1 April and 1 May.
        String loanId = disbursedLoan("1000.00", 2);
        jumpTo(START, LocalDate.of(2027, 4, 2));
        endOfDay("2027-04-02");

        JsonNode queue = api.get("/api/v1/loans/arrears", lender.token()).expect(200).data();
        assertThat(queue.get("items")).hasSize(1);
        assertThat(queue.at("/items/0/loanId").asString()).isEqualTo(loanId);
        assertThat(queue.at("/items/0/daysPastDue").asInt()).isEqualTo(1);
        assertThat(queue.at("/items/0/delinquencyBand").asString()).isEqualTo("WATCH");
        assertThat(queue.at("/items/0/overdue").asString()).isEqualTo("520.00");
        assertThat(queue.at("/items/0/lastActivity").isNull()).isTrue();

        String activities = "/api/v1/loans/" + loanId + "/collection-activities";
        api.post(activities, lender.token(), activity("CALL", "Called; she will pay this week", null, null))
                .expect(201);
        api.post(activities, lender.token(), activity("PROMISE", "Promised", null, null))
                .expectError(422, "BUSINESS_RULE_VIOLATION");
        api.post(activities, lender.token(), activity("VISIT", "Visited", "10.00", "2027-04-07"))
                .expectError(422, "BUSINESS_RULE_VIOLATION");
        api.post(activities, teller.token(), activity("CALL", "Called", null, null)).expectError(403, "ACCESS_DENIED");
        JsonNode promise = api.post(activities, lender.token(), activity("PROMISE", "Will pay in full on Wednesday",
                "520.00", "2027-04-07")).expect(201).data();
        assertThat(promise.get("promiseStatus").asString()).isEqualTo("OPEN");
        assertThat(promise.get("daysPastDue").asInt()).isEqualTo(1);
        assertThat(api.get("/api/v1/loans/arrears", lender.token()).expect(200).data()
                .at("/items/0/lastActivity/type").asString()).isEqualTo("PROMISE");

        repay(loanId, "520.00", "ACCOUNT", "promise-kept-" + loanId).expect(201);
        endOfDay("2027-04-05");
        assertThat(promiseStatuses(activities)).as("not decided before its date").containsExactly("OPEN");
        assertThat(api.get("/api/v1/loans/arrears", lender.token()).expect(200).data().get("items"))
                .as("paid up").isEmpty();
        endOfDay("2027-04-06");
        endOfDay("2027-04-07");
        assertThat(promiseStatuses(activities)).containsExactly("KEPT");

        api.post(activities, lender.token(), activity("PROMISE", "Will prepay 100", "100.00", "2027-04-08"))
                .expect(201);
        endOfDay("2027-04-08");
        assertThat(promiseStatuses(activities)).containsExactly("BROKEN", "KEPT");
        assertThat(api.get(activities, manager.token()).expect(200).data()).hasSize(3);
    }

    @Test
    void aRestructureReschedulesWhatIsOwedAndHoldsTheBand() {
        // 1,200 flat at 24%: six installments of 200 + 24 from 1 April. Two are missed by 3 May.
        String loanId = disbursedLoan("1200.00", 6);
        jumpTo(START, LocalDate.of(2027, 5, 3));
        endOfDay("2027-05-03");
        JsonNode before = loan(loanId);
        assertThat(before.at("/loan/delinquencyBand").asString()).isEqualTo("SUBSTANDARD");
        assertThat(before.at("/loan/provisionHeld").asString()).isEqualTo("300.00");
        JsonNode payoffBefore = api.get("/api/v1/loans/" + loanId + "/payoff", lender.token()).expect(200).data();

        JsonNode approval = api.post("/api/v1/loans/" + loanId + "/restructure", lender.token(), Map.of(
                "installments", 6, "holdBandDays", 60, "reason", "Shop was closed for repairs")).expect(202).data();
        assertThat(approval.get("status").asString()).isEqualTo("PENDING");
        assertThat(approval.get("requestType").asString()).isEqualTo("LOAN_RESTRUCTURE");
        assertThat(loan(loanId).at("/loan/scheduleVersion").asInt()).as("nothing changes before approval")
                .isEqualTo(1);
        api.post(decide(approval, "approve"), teller.token(), decision(approval)).expectError(403, "ACCESS_DENIED");
        JsonNode approved = api.post(decide(approval, "approve"), manager.token(), decision(approval)).expect(200)
                .data();
        assertThat(approved.get("status").asString()).isEqualTo("APPROVED");

        JsonNode after = loan(loanId);
        assertThat(after.at("/loan/scheduleVersion").asInt()).isEqualTo(2);
        assertThat(after.at("/loan/firstDueDate").asString()).isEqualTo("2027-06-04");
        assertThat(after.at("/loan/bandFloor").asString()).isEqualTo("SUBSTANDARD");
        assertThat(after.at("/loan/bandFloorUntil").asString()).isEqualTo("2027-07-03");
        assertThat(after.get("schedule")).hasSize(6);
        assertThat(after.at("/schedule/0/fromDate").asString()).isEqualTo("2027-05-04");
        String carried = after.at("/restructures/0/interestCarried").asString();
        assertThat(carried).as("interest earned and unpaid").isEqualTo(before.at("/payoff/interest").asString());
        assertThat(after.at("/schedule/0/interestDue").asString()).as("the month's 24.00 plus what was carried")
                .isEqualTo(new BigDecimal("24.00").add(new BigDecimal(carried)).toPlainString());
        BigDecimal principal = BigDecimal.ZERO;
        for (JsonNode installment : after.get("schedule")) {
            principal = principal.add(new BigDecimal(installment.get("principalDue").asString()));
        }
        assertThat(principal).isEqualByComparingTo("1200.00");
        for (String field : List.of("principal", "interest", "penalty", "total")) {
            assertThat(after.at("/payoff/" + field).asString()).as("what is owed today does not change: %s", field)
                    .isEqualTo(payoffBefore.get(field).asString());
        }
        assertThat(after.at("/restructures/0/requestedBy").asString()).isEqualTo(lender.id().toString());
        assertThat(after.at("/restructures/0/approvedBy").asString()).isEqualTo(manager.id().toString());

        // No longer late, but the loan keeps its band (and provision) until the hold ends.
        endOfDay("2027-05-04");
        JsonNode held = loan(loanId).get("loan");
        assertThat(held.get("daysPastDue").asInt()).isZero();
        assertThat(held.get("delinquencyBand").asString()).isEqualTo("SUBSTANDARD");
        assertThat(held.get("provisionHeld").asString()).isEqualTo("300.00");
        assertThat(held.get("interestReceivable").asString()).isEqualTo(carried);
    }

    @Test
    void aWriteOffUsesTheProvisionAndRecoveriesAreIncome() {
        String loanId = disbursedLoan("1000.00", 2);
        jumpTo(START, LocalDate.of(2027, 4, 2));
        endOfDay("2027-04-02");
        assertThat(loan(loanId).at("/loan/provisionHeld").asString()).isEqualTo("50.00");

        api.post("/api/v1/loans/" + loanId + "/write-off", lender.token(), Map.of("reason", "x"))
                .expectError(403, "ACCESS_DENIED");
        JsonNode approval = api.post("/api/v1/loans/" + loanId + "/write-off", manager.token(), Map.of(
                "reason", "Borrower left the country")).expect(202).data();
        api.post(decide(approval, "approve"), manager.token(), decision(approval))
                .expectError(403, "FOUR_EYES_VIOLATION");
        api.post(decide(approval, "approve"), secondManager.token(), decision(approval)).expect(200);

        JsonNode loan = loan(loanId).get("loan");
        assertThat(loan.get("status").asString()).isEqualTo("WRITTEN_OFF");
        assertThat(loan.get("closedOn").asString()).isEqualTo("2027-04-05");
        assertThat(loan.get("principalOutstanding").asString()).isEqualTo("0.00");
        assertThat(loan.get("interestReceivable").asString()).isEqualTo("0.00");
        assertThat(loan.get("provisionHeld").asString()).isEqualTo("0.00");
        // Interest earned to 5 April: 20.00 + 20.00 × 4/30.
        assertThat(loan.get("writtenOff").asString()).isEqualTo("1022.66");
        JsonNode trial = trialBalance();
        assertThat(trial.get("balanced").asBoolean()).isTrue();
        assertThat(glDebit(trial, "1210")).isEqualTo("0.00");
        assertThat(glCredit(trial, "1290")).as("the provision was used").isEqualTo("0.00");
        assertThat(glDebit(trial, "5200")).as("provision raised plus the shortfall").isEqualTo("1000.00");
        assertThat(glCredit(trial, "4100")).as("uncollected interest reversed").isEqualTo("0.00");
        repay(loanId, "10.00", "ACCOUNT", "after-write-off").expectError(422, "LOAN_NOT_ACTIVE");

        String recoveries = "/api/v1/loans/" + loanId + "/recoveries";
        Map<String, Object> recovery = Map.of("amount", "300.00", "source", "ACCOUNT");
        JsonNode receipt = api.postIdempotent(recoveries, teller.token(), "recovery-" + loanId, recovery)
                .expect(201).data();
        assertThat(api.postIdempotent(recoveries, teller.token(), "recovery-" + loanId, recovery).expect(201)
                .header("Idempotency-Replayed")).isEqualTo("true");
        assertThat(receipt.at("/loan/recovered").asString()).isEqualTo("300.00");
        assertThat(balance()).isEqualTo("675.00");
        api.postIdempotent(recoveries, teller.token(), "recovery-too-much-" + loanId, Map.of("amount", "722.67",
                "source", "ACCOUNT")).expectError(422, "OVER_RECOVERY");
        assertThat(glCredit(trialBalance(), "4250")).isEqualTo("300.00");
        assertThat(loan(loanId).get("recoveries")).hasSize(1);
    }

    // ---------------------------------------------------------------------------------------------------------

    private static Map<String, Object> activity(String type, String note, String amount, String date) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", type);
        request.put("note", note);
        if (amount != null) {
            request.put("promisedAmount", amount);
        }
        if (date != null) {
            request.put("promisedDate", date);
        }
        return request;
    }

    private List<String> promiseStatuses(String activities) {
        List<String> statuses = new ArrayList<>();
        api.get(activities, lender.token()).expect(200).data().forEach(activity -> {
            if ("PROMISE".equals(activity.get("type").asString())) {
                statuses.add(activity.get("promiseStatus").asString());
            }
        });
        return statuses;
    }

    private static String decide(JsonNode approval, String action) {
        return "/api/v1/approvals/" + approval.get("id").asString() + "/" + action;
    }

    private static Map<String, Object> decision(JsonNode approval) {
        return Map.of("note", "Checked", "version", approval.get("version").asLong());
    }
}
