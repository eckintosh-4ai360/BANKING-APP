package com.company.banking.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InterestCalculatorTest {

    @Test
    void oneDayAtThreePointSixFivePercentOnTenThousandIsExactlyOneUnit() {
        assertThat(InterestCalculator.dailyInterest(money("10000.0000"), money("3.65"), 1, 365))
                .isEqualTo(money("1.00000000"));
    }

    @Test
    void dailyInterestKeepsEightDecimalsRoundedHalfEven() {
        // 1234.56 x 5% / 365 = 0.16911780821...
        assertThat(InterestCalculator.dailyInterest(money("1234.56"), money("5"), 1, 365))
                .isEqualTo(money("0.16911781"));
        // 360-day year: 1000 x 4.5% / 360 = 0.125
        assertThat(InterestCalculator.dailyInterest(money("1000"), money("4.5"), 1, 360))
                .isEqualTo(money("0.12500000"));
    }

    @Test
    void nothingAccruesOnZeroOrNegativeBalancesOrWithoutARate() {
        assertThat(InterestCalculator.dailyInterest(BigDecimal.ZERO, money("5"), 1, 365)).isEqualByComparingTo("0");
        assertThat(InterestCalculator.dailyInterest(money("-50.00"), money("5"), 1, 365)).isEqualByComparingTo("0");
        assertThat(InterestCalculator.dailyInterest(money("50.00"), BigDecimal.ZERO, 1, 365)).isEqualByComparingTo("0");
        assertThat(InterestCalculator.dailyInterest(money("50.00"), money("5"), 0, 360)).isEqualByComparingTo("0");
    }

    @ParameterizedTest(name = "30/360 weight of {0} is {1}")
    @CsvSource({"2031-01-15, 1", "2031-01-31, 0", "2031-02-27, 1", "2031-02-28, 3", "2032-02-28, 1",
            "2032-02-29, 2", "2031-04-30, 1"})
    void thirtyThreeSixtyCountsEveryMonthAsThirtyDays(String date, int weight) {
        assertThat(InterestCalculator.dayWeight(LocalDate.parse(date), "THIRTY_360")).isEqualTo(weight);
        assertThat(InterestCalculator.dayWeight(LocalDate.parse(date), "ACTUAL_365F")).isEqualTo(1);
    }

    @Test
    void aFullMonthUnderThirtyThreeSixtyWeighsThirtyDays() {
        int february = 0;
        for (LocalDate day = LocalDate.of(2031, 2, 1); day.getMonthValue() == 2; day = day.plusDays(1)) {
            february += InterestCalculator.dayWeight(day, "THIRTY_360");
        }
        int march = 0;
        for (LocalDate day = LocalDate.of(2031, 3, 1); day.getMonthValue() == 3; day = day.plusDays(1)) {
            march += InterestCalculator.dayWeight(day, "THIRTY_360");
        }
        assertThat(february).isEqualTo(30);
        assertThat(march).isEqualTo(30);
    }

    @Test
    void postingTheChangeOfTheRoundedRunningTotalNeverDrifts() {
        BigDecimal exact = BigDecimal.ZERO;
        BigDecimal posted = BigDecimal.ZERO;
        BigDecimal daily = InterestCalculator.dailyInterest(money("1234.56"), money("5"), 1, 365);
        for (int day = 0; day < 31; day++) {
            BigDecimal next = exact.add(daily);
            posted = posted.add(InterestCalculator.glIncrement(exact, next, 2));
            exact = next;
            assertThat(posted).isEqualTo(InterestCalculator.toMinorUnits(exact, 2));
        }
        // 31 x 0.16911781 = 5.24265211
        assertThat(posted).isEqualTo(money("5.24"));
    }

    @Test
    void halfEvenRoundingHasNoUpwardBias() {
        assertThat(InterestCalculator.toMinorUnits(money("0.125"), 2)).isEqualTo(money("0.12"));
        assertThat(InterestCalculator.toMinorUnits(money("0.135"), 2)).isEqualTo(money("0.14"));
        assertThat(InterestCalculator.toMinorUnits(money("1550.5"), 0)).isEqualTo(money("1550"));
    }

    @Test
    void periodEndsFollowTheFrequency() {
        LocalDate from = LocalDate.of(2031, 3, 30);
        LocalDate to = LocalDate.of(2031, 4, 1);
        assertThat(InterestCalculator.periodEnds("MONTHLY", from, to)).containsExactly(LocalDate.of(2031, 3, 31));
        assertThat(InterestCalculator.periodEnds("QUARTERLY", from, to)).containsExactly(LocalDate.of(2031, 3, 31));
        assertThat(InterestCalculator.periodEnds("ANNUALLY", from, to)).isEmpty();
        assertThat(InterestCalculator.periodEnds("ANNUALLY", LocalDate.of(2031, 12, 31), LocalDate.of(2032, 1, 2)))
                .containsExactly(LocalDate.of(2031, 12, 31));
        assertThat(InterestCalculator.periodEnds("NONE", from, to)).isEmpty();
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
