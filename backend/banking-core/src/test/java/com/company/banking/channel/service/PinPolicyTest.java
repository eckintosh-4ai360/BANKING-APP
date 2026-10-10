package com.company.banking.channel.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PinPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"2580", "1357", "90210", "120394", "1123", "0990"})
    void ordinaryPinsAreAccepted(String pin) {
        assertThat(PinPolicy.isAcceptable(pin)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "4321", "0000", "999999", "3456789", "123", "12a4", "", " 2580", "56789",
            "876543"})
    void shortRepeatedRunsAndNonDigitsAreRefused(String pin) {
        assertThat(PinPolicy.isAcceptable(pin)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void noPinIsRefused() {
        assertThat(PinPolicy.isAcceptable(null)).isFalse();
    }
}
