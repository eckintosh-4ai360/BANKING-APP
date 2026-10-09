package com.company.banking.loan.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.loan.schedule.ScheduleCalculator.Installment;
import com.company.banking.loan.schedule.ScheduleCalculator.Schedule;
import com.company.banking.loan.schedule.ScheduleCalculator.Terms;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ScheduleCalculatorTest {

    private static Terms terms(String principal, String rate, InterestMethod method, RepaymentFrequency frequency,
                               LoanDayCount dayCount, int installments, LocalDate disbursed) {
        return new Terms(new BigDecimal(principal), new BigDecimal(rate), method, frequency, dayCount, installments,
                disbursed, null, 0, 0, 2, RoundingMode.HALF_EVEN);
    }

    @Test
    void theTextbookAnnuity() {
        // 10,000 at 12% a year, 12 monthly installments on 30/360: i = 1%, A = 10000 × 0.01 / (1 − 1.01⁻¹²).
        Schedule schedule = ScheduleCalculator.calculate(terms("10000.00", "12", InterestMethod.DECLINING_BALANCE_EQUAL_INSTALLMENT,
                RepaymentFrequency.MONTHLY, LoanDayCount.THIRTY_360, 12, LocalDate.of(2027, 1, 15)));

        assertThat(schedule.installmentAmount()).isEqualByComparingTo("888.49");
        assertThat(schedule.installments().getFirst().interest()).isEqualByComparingTo("100.00");
        assertThat(schedule.installments().getFirst().principal()).isEqualByComparingTo("788.49");
        assertThat(schedule.installments().subList(0, 11)).allSatisfy(installment ->
                assertThat(installment.total()).isEqualByComparingTo("888.49"));
        assertThat(schedule.totalInterest()).isEqualByComparingTo(schedule.installments().stream()
                .map(Installment::total).reduce(BigDecimal.ZERO, BigDecimal::add).subtract(new BigDecimal("10000")));
        assertThat(schedule.maturityDate()).isEqualTo(LocalDate.of(2028, 1, 15));
    }

    @Test
    void flatInterestIsChargedOnTheOriginalPrincipal() {
        Schedule schedule = ScheduleCalculator.calculate(terms("1000.00", "24", InterestMethod.FLAT,
                RepaymentFrequency.MONTHLY, LoanDayCount.THIRTY_360, 6, LocalDate.of(2027, 1, 15)));

        assertThat(schedule.installments()).extracting(Installment::interest)
                .allSatisfy(interest -> assertThat(interest).isEqualByComparingTo("20.00"));
        assertThat(schedule.installments()).extracting(installment -> installment.principal().toPlainString())
                .containsExactly("166.66", "166.66", "166.66", "166.66", "166.66", "166.70");
        assertThat(schedule.totalInterest()).isEqualByComparingTo("120.00");
    }

    @Test
    void equalPrincipalInterestFollowsTheOutstandingBalance() {
        Schedule schedule = ScheduleCalculator.calculate(terms("1200.00", "12", InterestMethod.DECLINING_BALANCE_EQUAL_PRINCIPAL,
                RepaymentFrequency.MONTHLY, LoanDayCount.THIRTY_360, 3, LocalDate.of(2027, 1, 15)));

        assertThat(schedule.installments()).extracting(installment -> installment.principal().toPlainString())
                .containsExactly("400.00", "400.00", "400.00");
        assertThat(schedule.installments()).extracting(installment -> installment.interest().toPlainString())
                .containsExactly("12.00", "8.00", "4.00");
    }

    @Test
    void anIrregularFirstPeriodIsChargedForItsActualDays() {
        Terms terms = new Terms(new BigDecimal("3650.00"), new BigDecimal("10"), InterestMethod.DECLINING_BALANCE_EQUAL_PRINCIPAL,
                RepaymentFrequency.MONTHLY, LoanDayCount.ACTUAL_365F, 2, LocalDate.of(2027, 3, 1),
                LocalDate.of(2027, 3, 21), 0, 0, 2, RoundingMode.HALF_EVEN);
        Schedule schedule = ScheduleCalculator.calculate(terms);

        // 20 days on 3,650 at 10%: 3650 × 0.10 × 20 / 365 = 20.00; then 31 days on 1,825: 15.50.
        assertThat(schedule.installments().get(0).interest()).isEqualByComparingTo("20.00");
        assertThat(schedule.installments().get(1).dueDate()).isEqualTo(LocalDate.of(2027, 4, 21));
        assertThat(schedule.installments().get(1).interest()).isEqualByComparingTo("15.50");
    }

    @Test
    void graceDefersPrincipalAndInterest() {
        Terms terms = new Terms(new BigDecimal("1200.00"), new BigDecimal("12"), InterestMethod.DECLINING_BALANCE_EQUAL_PRINCIPAL,
                RepaymentFrequency.MONTHLY, LoanDayCount.THIRTY_360, 5, LocalDate.of(2027, 1, 15), null, 2, 1,
                2, RoundingMode.HALF_EVEN);
        Schedule schedule = ScheduleCalculator.calculate(terms);

        assertThat(schedule.installments()).extracting(installment -> installment.principal().toPlainString())
                .containsExactly("0.00", "0.00", "400.00", "400.00", "400.00");
        // Nothing due in month 1; its 12.00 falls due with month 2's 12.00.
        assertThat(schedule.installments()).extracting(installment -> installment.interest().toPlainString())
                .containsExactly("0.00", "24.00", "12.00", "8.00", "4.00");
    }

    @Test
    void thirtyThreeSixtyCountsEveryMonthAsThirtyDays() {
        assertThat(LoanDayCount.days360(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28))).isEqualTo(28);
        assertThat(LoanDayCount.days360(LocalDate.of(2027, 1, 30), LocalDate.of(2027, 3, 31))).isEqualTo(60);
        assertThat(LoanDayCount.days360(LocalDate.of(2027, 1, 15), LocalDate.of(2028, 1, 15))).isEqualTo(360);
        assertThat(LoanDayCount.days360(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31))).isEqualTo(30);
    }

    @Test
    void monthlyDueDatesKeepTheDayOfTheFirstDueDate() {
        Schedule schedule = ScheduleCalculator.calculate(terms("600.00", "0", InterestMethod.FLAT,
                RepaymentFrequency.MONTHLY, LoanDayCount.ACTUAL_365F, 4, LocalDate.of(2026, 12, 31)));
        assertThat(schedule.installments()).extracting(Installment::dueDate).containsExactly(
                LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30));
    }

    @Test
    void refusesTermsThatCannotBeScheduled() {
        LocalDate day = LocalDate.of(2027, 1, 1);
        assertThatThrownBy(() -> terms("100.001", "10", InterestMethod.FLAT, RepaymentFrequency.MONTHLY,
                LoanDayCount.ACTUAL_365F, 3, day)).hasMessageContaining("minor units");
        assertThatThrownBy(() -> terms("100.00", "-1", InterestMethod.FLAT, RepaymentFrequency.MONTHLY,
                LoanDayCount.ACTUAL_365F, 3, day)).hasMessageContaining("negative");
        assertThatThrownBy(() -> new Terms(new BigDecimal("100"), BigDecimal.TEN, InterestMethod.FLAT,
                RepaymentFrequency.MONTHLY, LoanDayCount.ACTUAL_365F, 3, day, null, 3, 0, 2, RoundingMode.HALF_EVEN))
                .hasMessageContaining("amortising");
        assertThatThrownBy(() -> new Terms(new BigDecimal("100"), BigDecimal.TEN, InterestMethod.FLAT,
                RepaymentFrequency.MONTHLY, LoanDayCount.ACTUAL_365F, 3, day, null, 1, 2, 2, RoundingMode.HALF_EVEN))
                .hasMessageContaining("interest grace");
        assertThatThrownBy(() -> new Terms(new BigDecimal("100"), BigDecimal.TEN, InterestMethod.FLAT,
                RepaymentFrequency.MONTHLY, LoanDayCount.ACTUAL_365F, 3, day, day, 0, 0, 2, RoundingMode.HALF_EVEN))
                .hasMessageContaining("after disbursement");
    }

    /**
     * The gate: for every method, frequency and day count, over random amounts, rates, terms, start dates and grace,
     * principal adds up to exactly the amount disbursed, nothing is negative, the balance only goes down and ends at
     * zero, and due dates move forward.
     */
    @Test
    void principalAlwaysAddsUpToTheAmountDisbursed() {
        Random random = new Random(20270101);
        int checked = 0;
        for (InterestMethod method : InterestMethod.values()) {
            for (RepaymentFrequency frequency : RepaymentFrequency.values()) {
                for (LoanDayCount dayCount : LoanDayCount.values()) {
                    for (int trial = 0; trial < 45; trial++) {
                        int installments = 1 + random.nextInt(frequency == RepaymentFrequency.DAILY ? 120 : 48);
                        int principalGrace = installments > 1 ? random.nextInt(Math.min(installments, 4)) : 0;
                        int interestGrace = principalGrace == 0 ? 0 : random.nextInt(principalGrace + 1);
                        BigDecimal principal = BigDecimal.valueOf(1 + random.nextInt(50_000_000), 2);
                        BigDecimal rate = BigDecimal.valueOf(random.nextInt(9_000_000), 5);
                        LocalDate disbursed = LocalDate.of(2026, 1, 1).plusDays(random.nextInt(900));
                        Terms terms = new Terms(principal, rate, method, frequency, dayCount, installments, disbursed,
                                null, principalGrace, interestGrace, 2,
                                random.nextBoolean() ? RoundingMode.HALF_EVEN : RoundingMode.HALF_UP);
                        check(terms, ScheduleCalculator.calculate(terms));
                        checked++;
                    }
                }
            }
        }
        assertThat(checked).isEqualTo(2025);
    }

    private static void check(Terms terms, Schedule schedule) {
        List<Installment> installments = schedule.installments();
        assertThat(installments).hasSize(terms.installments());
        assertThat(installments.stream().map(Installment::principal).reduce(BigDecimal.ZERO, BigDecimal::add))
                .as("%s", terms).isEqualByComparingTo(terms.principal());
        assertThat(installments.stream().map(Installment::interest).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(schedule.totalInterest());
        assertThat(installments.getLast().outstandingAfter()).isEqualByComparingTo(BigDecimal.ZERO);
        BigDecimal previous = terms.principal();
        LocalDate previousDue = terms.disbursementDate();
        for (Installment installment : installments) {
            assertThat(installment.principal().signum()).as("%s", terms).isGreaterThanOrEqualTo(0);
            assertThat(installment.interest().signum()).as("%s", terms).isGreaterThanOrEqualTo(0);
            assertThat(installment.principal().scale()).isEqualTo(2);
            assertThat(installment.interest().scale()).isEqualTo(2);
            assertThat(installment.outstandingAfter()).isLessThanOrEqualTo(previous);
            assertThat(installment.dueDate()).isAfter(previousDue);
            assertThat(installment.total()).isEqualByComparingTo(installment.principal().add(installment.interest()));
            previous = installment.outstandingAfter();
            previousDue = installment.dueDate();
        }
        for (int k = 0; k < terms.principalGrace(); k++) {
            assertThat(installments.get(k).principal().signum()).isZero();
        }
        for (int k = 0; k < terms.interestGrace(); k++) {
            assertThat(installments.get(k).interest().signum()).isZero();
        }
    }
}
