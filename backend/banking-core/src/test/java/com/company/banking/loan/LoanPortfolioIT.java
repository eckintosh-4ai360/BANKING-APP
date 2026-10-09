package com.company.banking.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.support.EndOfDayProbes;
import com.company.banking.support.EndOfDayProbes.StopProbe;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

/**
 * Loan end-of-day: interest recognised day by day, penalties on overdue installments, days past due and the
 * delinquency band, interest and penalties moved into suspense when accrual is suspended (and back when the loan is
 * cured), and the provision each band requires. A run stopped after a batch resumes without doing anything twice.
 *
 * <p>The loan: 1,000 flat at 24% (30/360) in two monthly installments of 500 + 20, due 1 April and 1 May 2027; a
 * penalty of 36.5% a year (1.00 a day on 1,000 overdue) from the day after the due date.
 */
@Import(EndOfDayProbes.class)
class LoanPortfolioIT extends LoanIntegrationTest {

    @Autowired
    private StopProbe probe;

    @BeforeEach
    void setUpProductAndBands() {
        Map<String, Object> terms = loanTerms();
        terms.put("penaltyRate", "36.5");
        productId = publishedLoanProduct("PEN01", terms);
        api.put("/api/v1/loan-portfolio/delinquency-bands", tenant.adminToken(), Map.of("bands", List.of(
                band("CURRENT", 0, "1", false), band("WATCH", 1, "10", false), band("DOUBTFUL", 3, "50", true))))
                .expect(200);
    }

    @AfterEach
    void disarm() {
        probe.armedStep = null;
    }

    @Test
    void endOfDayAccruesPenalisesClassifiesSuspendsAndProvisions() {
        String loanId = disbursedLoan("1000.00", 2);
        assertThat(balance()).isEqualTo("975.00");
        jumpTo(START, LocalDate.of(2027, 3, 31));

        endOfDay("2027-03-31");
        JsonNode loan = loan(loanId).get("loan");
        assertThat(loan.get("interestReceivable").asString()).as("20.00 × 30/31 days, rounded down")
                .isEqualTo("19.35");
        assertThat(loan.get("delinquencyBand").asString()).isEqualTo("CURRENT");
        assertThat(loan.get("provisionHeld").asString()).isEqualTo("10.00");

        endOfDay("2027-04-01");
        loan = loan(loanId).get("loan");
        assertThat(loan.get("interestReceivable").asString()).isEqualTo("20.00");
        assertThat(loan.get("daysPastDue").asInt()).as("due today is not late yet").isZero();

        // Friday: one day late. A crash after the batch committed must not penalise or provision twice.
        probe.armedStep = "LOAN_PORTFOLIO";
        probe.armedPoint = "batch";
        JsonNode failed = api.post("/api/v1/operations/eod", tenant.adminToken(), null).expect(202).data();
        assertThat(failed.get("status").asString()).isEqualTo("FAILED");
        assertThat(api.post("/api/v1/operations/eod/" + failed.get("id").asString() + "/resume",
                tenant.adminToken(), null).expect(202).data().get("status").asString()).isEqualTo("COMPLETED");
        loan = loan(loanId).get("loan");
        assertThat(loan.get("daysPastDue").asInt()).isEqualTo(1);
        assertThat(loan.get("delinquencyBand").asString()).isEqualTo("WATCH");
        assertThat(loan.get("penaltyReceivable").asString()).as("520 overdue for a day").isEqualTo("0.52");
        assertThat(loan.get("interestReceivable").asString()).isEqualTo("20.66");
        assertThat(loan.get("provisionHeld").asString()).isEqualTo("100.00");

        // Monday closes the weekend too: three more penalty days, and four days late suspends accrual.
        endOfDay("2027-04-05");
        loan = loan(loanId).get("loan");
        assertThat(loan.get("daysPastDue").asInt()).isEqualTo(4);
        assertThat(loan.get("delinquencyBand").asString()).isEqualTo("DOUBTFUL");
        assertThat(loan.get("nonAccrual").asBoolean()).isTrue();
        assertThat(loan.get("penaltyReceivable").asString()).isEqualTo("2.08");
        assertThat(loan.get("interestReceivable").asString()).isEqualTo("22.66");
        assertThat(loan.get("provisionHeld").asString()).isEqualTo("500.00");
        JsonNode trial = trialBalance();
        assertThat(trial.get("balanced").asBoolean()).isTrue();
        assertThat(glCredit(trial, "1295")).as("uncollected interest and penalties held in suspense")
                .isEqualTo("24.74");
        assertThat(glCredit(trial, "4100")).isEqualTo("0.00");
        assertThat(glCredit(trial, "4240")).isEqualTo("0.00");
        assertThat(glCredit(trial, "1290")).isEqualTo("500.00");

        JsonNode portfolio = api.get("/api/v1/loan-portfolio", manager.token()).expect(200).data().get(0);
        assertThat(portfolio.get("activeLoans").asLong()).isEqualTo(1);
        assertThat(portfolio.get("principalOutstanding").asString()).isEqualTo("1000.00");
        assertThat(portfolio.get("portfolioAtRisk30").asString()).isEqualTo("0.00");
        assertThat(portfolio.get("nonAccrualLoans").asLong()).isEqualTo(1);
        assertThat(portfolio.at("/bands/0/code").asString()).isEqualTo("DOUBTFUL");
        assertThat(portfolio.at("/bands/0/provisionHeld").asString()).isEqualTo("500.00");

        endOfDay("2027-04-06");
        assertThat(loan(loanId).at("/loan/penaltyReceivable").asString()).isEqualTo("2.60");

        // Paying the overdue installment (penalty first) collects its interest and penalties out of suspense.
        JsonNode receipt = repay(loanId, "522.60", "ACCOUNT", "cure-" + loanId).expect(201).data();
        assertThat(receipt.at("/repayment/penalty").asString()).isEqualTo("2.60");
        assertThat(receipt.at("/repayment/interest").asString()).isEqualTo("20.00");
        assertThat(receipt.at("/repayment/principal").asString()).isEqualTo("500.00");
        assertThat(balance()).isEqualTo("452.40");

        // Cured: accrual resumes (the 4.00 earned on the second installment goes back to income).
        endOfDay("2027-04-07");
        JsonNode detail = loan(loanId);
        loan = detail.get("loan");
        assertThat(loan.get("delinquencyBand").asString()).isEqualTo("CURRENT");
        assertThat(loan.get("nonAccrual").asBoolean()).isFalse();
        assertThat(loan.get("daysPastDue").asInt()).isZero();
        assertThat(loan.get("interestReceivable").asString()).isEqualTo("4.00");
        assertThat(loan.get("provisionHeld").asString()).isEqualTo("5.00");
        assertThat(statuses(detail)).containsExactly("PAID", "UPCOMING");
        assertThat(detail.at("/schedule/0/penaltyDue").asString()).isEqualTo("2.60");
        trial = trialBalance();
        assertThat(trial.get("balanced").asBoolean()).isTrue();
        assertThat(glCredit(trial, "1295")).isEqualTo("0.00");
        assertThat(glCredit(trial, "4100")).isEqualTo("24.00");
        assertThat(glCredit(trial, "4240")).isEqualTo("2.60");
        assertThat(glCredit(trial, "1290")).isEqualTo("5.00");
        assertThat(glDebit(trial, "5200")).isEqualTo("5.00");
    }

    @Test
    void bandsAreALadderStartingAtZero() {
        assertThat(api.get("/api/v1/loan-portfolio/delinquency-bands", lender.token()).expect(200).data())
                .extracting(band -> band.get("code").asString()).containsExactly("CURRENT", "WATCH", "DOUBTFUL");
        String path = "/api/v1/loan-portfolio/delinquency-bands";
        api.put(path, tenant.adminToken(), Map.of("bands", List.of(band("LATE", 1, "5", false))))
                .expectError(422, "INVALID_BANDS");
        api.put(path, tenant.adminToken(), Map.of("bands", List.of(band("CURRENT", 0, "5", false),
                band("WATCH", 30, "1", false)))).expectError(422, "INVALID_BANDS");
        api.put(path, tenant.adminToken(), Map.of("bands", List.of(band("CURRENT", 0, "1", true),
                band("WATCH", 30, "5", false)))).expectError(422, "INVALID_BANDS");
        api.put(path, manager.token(), Map.of("bands", List.of(band("CURRENT", 0, "1", false))))
                .expectError(403, "ACCESS_DENIED");
    }

    private static Map<String, Object> band(String code, int minDays, String rate, boolean suspend) {
        return Map.of("code", code, "name", code.charAt(0) + code.substring(1).toLowerCase(), "minDays", minDays,
                "provisionRate", rate, "suspendAccrual", suspend);
    }
}
