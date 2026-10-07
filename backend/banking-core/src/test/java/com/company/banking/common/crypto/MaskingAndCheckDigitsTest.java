package com.company.banking.common.crypto;

import com.company.banking.common.sequence.CheckDigits;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingAndCheckDigitsTest {

    @Test
    void masksAllButTheLastFourCharactersAndKeepsSeparators() {
        assertThat(Masking.maskIdentifier("GHA-123456789-0")).isEqualTo("***-******789-0");
        assertThat(Masking.maskIdentifier("A1234567")).isEqualTo("****4567");
        assertThat(Masking.maskIdentifier("123")).isEqualTo("123");
        assertThat(Masking.maskIdentifier(null)).isNull();
    }

    @Test
    void luhnMatchesTheStandardExample() {
        assertThat(CheckDigits.luhn("7992739871")).isEqualTo(3);
        assertThat(CheckDigits.isValidLuhn("79927398713")).isTrue();
        assertThat(CheckDigits.isValidLuhn("79927398710")).isFalse();
    }

    @Test
    void appendedCheckDigitCatchesSingleDigitTyposAndTranspositions() {
        String number = CheckDigits.appendLuhn("000001234");
        assertThat(CheckDigits.isValidLuhn(number)).isTrue();
        assertThat(CheckDigits.isValidLuhn("000001235" + number.charAt(number.length() - 1))).isFalse();
        assertThat(CheckDigits.isValidLuhn("000002134" + number.charAt(number.length() - 1))).isFalse();
    }
}
