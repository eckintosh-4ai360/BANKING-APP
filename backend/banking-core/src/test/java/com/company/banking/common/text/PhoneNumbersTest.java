package com.company.banking.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void nationalNumbersTakeTheInstitutionsCountryCode() {
        assertThat(PhoneNumbers.normalize("024 123 4567", "GH")).hasValue("+233241234567");
        assertThat(PhoneNumbers.normalize("0241-234-567", "GH")).hasValue("+233241234567");
        assertThat(PhoneNumbers.normalize("0803 123 4567", "NG")).hasValue("+2348031234567");
    }

    @Test
    void internationalNumbersKeepTheirCountryCode() {
        assertThat(PhoneNumbers.normalize("+233241234567", "GH")).hasValue("+233241234567");
        assertThat(PhoneNumbers.normalize("00233241234567", "NG")).hasValue("+233241234567");
        assertThat(PhoneNumbers.normalize("+44 20 7946 0958", "GH")).hasValue("+442079460958");
    }

    @Test
    void anythingElseIsNotAPhoneNumber() {
        assertThat(PhoneNumbers.normalize("0241234567", "XX")).as("unknown calling code").isEmpty();
        assertThat(PhoneNumbers.normalize("241234567", "GH")).as("no prefix").isEmpty();
        assertThat(PhoneNumbers.normalize("+0241234567", "GH")).isEmpty();
        assertThat(PhoneNumbers.normalize("+23324abc4567", "GH")).isEmpty();
        assertThat(PhoneNumbers.normalize("+1234", "GH")).as("too short").isEmpty();
        assertThat(PhoneNumbers.normalize(null, "GH")).isEmpty();
    }

    @Test
    void maskingShowsOnlyTheEnd() {
        assertThat(PhoneNumbers.mask("+233241234567")).isEqualTo("+233******567");
        assertThat(PhoneNumbers.mask("+123")).isEqualTo("***");
    }
}
