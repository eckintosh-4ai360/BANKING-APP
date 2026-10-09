package com.company.banking.loan.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.loan.schedule.LoanArrears.Band;
import com.company.banking.loan.schedule.LoanArrears.Owing;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoanArrearsTest {

    private static final LocalDate DUE = LocalDate.of(2027, 4, 1);

    private static final List<Band> BANDS = List.of(
            new Band("CURRENT", 0, new BigDecimal("1"), false),
            new Band("WATCH", 1, new BigDecimal("5"), false),
            new Band("SUBSTANDARD", 31, new BigDecimal("25"), false),
            new Band("DOUBTFUL", 91, new BigDecimal("50"), true),
            new Band("LOSS", 181, new BigDecimal("100"), true));

    @Test
    void daysPastDueCountFromTheOldestInstallmentStillOwing() {
        List<Owing> installments = List.of(
                new Owing(LocalDate.of(2027, 3, 1), BigDecimal.ZERO),
                new Owing(DUE, new BigDecimal("12.50")),
                new Owing(LocalDate.of(2027, 5, 1), new BigDecimal("224.00")));

        assertThat(LoanArrears.daysPastDue(installments, LocalDate.of(2027, 3, 31))).isZero();
        assertThat(LoanArrears.daysPastDue(installments, DUE)).as("due today is not late yet").isZero();
        assertThat(LoanArrears.daysPastDue(installments, LocalDate.of(2027, 4, 2))).isEqualTo(1);
        assertThat(LoanArrears.daysPastDue(installments, LocalDate.of(2027, 5, 10))).isEqualTo(39);
    }

    @Test
    void penaltyDaysStartAfterTheGracePeriodAndAreNeverCountedTwice() {
        // Grace 3: the first penalty day is 5 April.
        assertThat(LoanArrears.penaltyDays(DUE, 3, LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 4))).isZero();
        assertThat(LoanArrears.penaltyDays(DUE, 3, LocalDate.of(2027, 4, 4), LocalDate.of(2027, 4, 5)))
                .isEqualTo(1);
        // A weekend closed with Monday: Saturday to Monday.
        assertThat(LoanArrears.penaltyDays(DUE, 0, LocalDate.of(2027, 4, 9), LocalDate.of(2027, 4, 12)))
                .isEqualTo(3);
        // Day by day or all at once, the same days.
        long stepwise = 0;
        for (LocalDate day = DUE; day.isBefore(LocalDate.of(2027, 6, 30)); day = day.plusDays(1)) {
            stepwise += LoanArrears.penaltyDays(DUE, 7, day, day.plusDays(1));
        }
        assertThat(stepwise).isEqualTo(LoanArrears.penaltyDays(DUE, 7, DUE, LocalDate.of(2027, 6, 30)))
                .isEqualTo(83);
    }

    @Test
    void penaltyIsSimpleInterestOnTheOverdueAmount() {
        // 1,000 overdue at 36.5% a year: exactly 1.00 a day.
        assertThat(LoanArrears.penalty(new BigDecimal("1000.00"), new BigDecimal("36.5"), 30))
                .isEqualByComparingTo("30");
        BigDecimal daily = LoanArrears.penalty(new BigDecimal("224.00"), new BigDecimal("24"), 1);
        assertThat(daily.multiply(BigDecimal.valueOf(365)).setScale(2, RoundingMode.HALF_EVEN))
                .isEqualByComparingTo("53.76");
        assertThat(LoanArrears.penalty(BigDecimal.ZERO, new BigDecimal("24"), 5)).isZero();
        assertThat(LoanArrears.penalty(new BigDecimal("100"), BigDecimal.ZERO, 5)).isZero();
    }

    @Test
    void provisionIsTheBandsShareOfPrincipalRoundedHalfUp() {
        assertThat(LoanArrears.provision(new BigDecimal("1000.00"), new BigDecimal("25"), 2))
                .isEqualByComparingTo("250.00");
        assertThat(LoanArrears.provision(new BigDecimal("333.33"), new BigDecimal("5"), 2))
                .isEqualByComparingTo("16.67");
        assertThat(LoanArrears.provision(new BigDecimal("0.10"), new BigDecimal("25"), 2))
                .as("0.025 rounds up").isEqualByComparingTo("0.03");
    }

    @Test
    void theBandIsTheHighestStartingAtOrBelowTheDaysPastDue() {
        assertThat(LoanArrears.bandFor(BANDS, 0).code()).isEqualTo("CURRENT");
        assertThat(LoanArrears.bandFor(BANDS, 30).code()).isEqualTo("WATCH");
        assertThat(LoanArrears.bandFor(BANDS, 31).code()).isEqualTo("SUBSTANDARD");
        assertThat(LoanArrears.bandFor(BANDS, 91).suspendAccrual()).isTrue();
        assertThat(LoanArrears.bandFor(BANDS, 4000).code()).isEqualTo("LOSS");
        assertThatThrownBy(() -> LoanArrears.bandFor(BANDS.subList(1, 3), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
