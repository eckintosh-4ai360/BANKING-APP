package com.company.banking.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.product.dto.ChargeTerms;
import com.company.banking.product.service.ChargeCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChargeCalculatorTest {

    @Test
    void aFlatChargeIsTheConfiguredAmountWhateverIsMoved() {
        ChargeTerms flat = new ChargeTerms("CASH_WITHDRAWAL", "Withdrawal fee", "FLAT", money("2.5000"), null,
                null, null);

        assertThat(ChargeCalculator.calculate(flat, money("0.01"), 2)).isEqualTo(money("2.50"));
        assertThat(ChargeCalculator.calculate(flat, money("1000000.00"), 2)).isEqualTo(money("2.50"));
    }

    @ParameterizedTest(name = "{1}% of {0} = {2}")
    @CsvSource({
            "100.00, 1.5, 1.50",
            "33.33, 1.5, 0.50",     // 0.49995 rounds half up
            "33.32, 1.5, 0.50",     // 0.4998
            "10.00, 0.125, 0.01",   // 0.0125
            "10.00, 0.124, 0.01",   // 0.0124
            "0.33, 1.5, 0.00",      // 0.00495 rounds down
            "0.34, 1.5, 0.01",      // 0.0051
            "999999999.99, 2.5, 25000000.00"})
    void aPercentageChargeRoundsHalfUpToTheMinorUnit(String amount, String rate, String expected) {
        ChargeTerms percent = new ChargeTerms("TRANSFER_OUT", "Transfer fee", "PERCENT", null, money(rate), null,
                null);

        assertThat(ChargeCalculator.calculate(percent, money(amount), 2)).isEqualTo(money(expected));
    }

    @Test
    void aPercentageChargeStaysWithinItsMinimumAndMaximum() {
        ChargeTerms bounded = new ChargeTerms("CASH_WITHDRAWAL", "Withdrawal fee", "PERCENT", null, money("1"),
                money("1.0000"), money("5.0000"));

        assertThat(ChargeCalculator.calculate(bounded, money("20.00"), 2)).isEqualTo(money("1.00"));
        assertThat(ChargeCalculator.calculate(bounded, money("300.00"), 2)).isEqualTo(money("3.00"));
        assertThat(ChargeCalculator.calculate(bounded, money("5000.00"), 2)).isEqualTo(money("5.00"));
    }

    @Test
    void currenciesWithoutMinorUnitsRoundToWholeUnits() {
        ChargeTerms percent = new ChargeTerms("CASH_DEPOSIT", "Deposit fee", "PERCENT", null, money("1"), null,
                null);

        assertThat(ChargeCalculator.calculate(percent, money("1550"), 0)).isEqualTo(money("16"));
        assertThat(ChargeCalculator.calculate(percent, money("1549"), 0)).isEqualTo(money("15"));
    }

    @Test
    void onlyPositiveAmountsAreCharged() {
        ChargeTerms flat = new ChargeTerms("CASH_DEPOSIT", "Deposit fee", "FLAT", money("1.00"), null, null, null);

        assertThatThrownBy(() -> ChargeCalculator.calculate(flat, BigDecimal.ZERO, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChargeCalculator.calculate(flat, money("-1.00"), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
