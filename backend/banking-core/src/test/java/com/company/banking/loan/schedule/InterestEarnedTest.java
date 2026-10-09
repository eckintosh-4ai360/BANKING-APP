package com.company.banking.loan.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.loan.schedule.InterestEarned.Period;
import com.company.banking.loan.schedule.ScheduleCalculator.Schedule;
import com.company.banking.loan.schedule.ScheduleCalculator.Terms;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class InterestEarnedTest {

    private static final LocalDate DISBURSED = LocalDate.of(2027, 3, 1);

    private static List<Period> periods(Schedule schedule) {
        return schedule.installments().stream()
                .map(installment -> new Period(installment.fromDate(), installment.dueDate(), installment.interest()))
                .toList();
    }

    @Test
    void earnsEachInstallmentEvenlyOverItsPeriod() {
        List<Period> periods = List.of(
                new Period(DISBURSED, LocalDate.of(2027, 3, 31), new BigDecimal("30.00")),
                new Period(LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30), new BigDecimal("20.00")));

        assertThat(InterestEarned.through(periods, DISBURSED, DISBURSED, 2)).isEqualByComparingTo("0.00");
        assertThat(InterestEarned.through(periods, DISBURSED, LocalDate.of(2027, 3, 11), 2))
                .isEqualByComparingTo("10.00");
        assertThat(InterestEarned.through(periods, DISBURSED, LocalDate.of(2027, 3, 31), 2))
                .isEqualByComparingTo("30.00");
        // 30 + 20 × 7/30 = 34.666…, rounded down.
        assertThat(InterestEarned.through(periods, DISBURSED, LocalDate.of(2027, 4, 7), 2))
                .isEqualByComparingTo("34.66");
        assertThat(InterestEarned.through(periods, DISBURSED, LocalDate.of(2027, 6, 1), 2))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void interestDeferredByGraceIsEarnedOverTheWholeDeferredStretch() {
        // Two months of interest grace: the third installment carries three months of interest (90.00).
        List<Period> periods = List.of(
                new Period(DISBURSED, LocalDate.of(2027, 3, 31), new BigDecimal("0.00")),
                new Period(LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30), new BigDecimal("0.00")),
                new Period(LocalDate.of(2027, 4, 30), LocalDate.of(2027, 5, 30), new BigDecimal("90.00")));

        // 30 of 90 days: a third.
        assertThat(InterestEarned.through(periods, DISBURSED, LocalDate.of(2027, 3, 31), 2))
                .isEqualByComparingTo("30.00");
        assertThat(InterestEarned.through(periods, DISBURSED, LocalDate.of(2027, 5, 30), 2))
                .isEqualByComparingTo("90.00");
    }

    @Test
    void neverDecreasesNeverOvershootsAndMatchesWhatFellDueOnEachDueDate() {
        Random random = new Random(20270301);
        for (int run = 0; run < 200; run++) {
            InterestMethod method = InterestMethod.values()[random.nextInt(InterestMethod.values().length)];
            RepaymentFrequency frequency =
                    RepaymentFrequency.values()[random.nextInt(RepaymentFrequency.values().length)];
            int installments = 1 + random.nextInt(24);
            int principalGrace = random.nextInt(Math.min(installments, 4));
            int interestGrace = random.nextInt(principalGrace + 1);
            Schedule schedule = ScheduleCalculator.calculate(new Terms(
                    BigDecimal.valueOf(10_000 + random.nextInt(5_000_000), 2), BigDecimal.valueOf(random.nextInt(60)),
                    method, frequency, LoanDayCount.values()[random.nextInt(LoanDayCount.values().length)],
                    installments, DISBURSED, null, principalGrace, interestGrace, 2, RoundingMode.HALF_EVEN));
            List<Period> periods = periods(schedule);

            BigDecimal previous = BigDecimal.ZERO;
            BigDecimal dueSoFar = BigDecimal.ZERO;
            int next = 0;
            for (LocalDate day = DISBURSED; !day.isAfter(schedule.maturityDate()); day = day.plusDays(1)) {
                BigDecimal earned = InterestEarned.through(periods, DISBURSED, day, 2);
                while (next < periods.size() && !periods.get(next).dueDate().isAfter(day)) {
                    dueSoFar = dueSoFar.add(periods.get(next++).interestDue());
                }
                assertThat(earned).isGreaterThanOrEqualTo(previous).isGreaterThanOrEqualTo(dueSoFar)
                        .isLessThanOrEqualTo(schedule.totalInterest());
                // Interest deferred by grace is earned before it falls due, so only due dates with interest due match.
                if (next > 0 && periods.get(next - 1).dueDate().equals(day)
                        && periods.get(next - 1).interestDue().signum() > 0) {
                    assertThat(earned).isEqualByComparingTo(dueSoFar);
                }
                previous = earned;
            }
            assertThat(previous).isEqualByComparingTo(schedule.totalInterest());
        }
    }
}
