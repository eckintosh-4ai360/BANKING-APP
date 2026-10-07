package com.company.banking.iam.service;

import com.company.banking.common.error.BankingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordServiceTest {

    private final PasswordService service = new PasswordService(new Argon2PasswordEncoder(16, 32, 1, 1024, 1));

    @ParameterizedTest
    @ValueSource(strings = {"short", "elevenchars", "aaaaaaaaaaaaaaaa", "Password1234", "my-password123!",
            "qwertyuiop12"})
    void rejectsWeakPasswords(String candidate) {
        assertThatThrownBy(() -> service.validateNewPassword(candidate, "kofi.mensah", null))
                .isInstanceOf(BankingException.class)
                .extracting(ex -> ((BankingException) ex).getErrorCode().code())
                .isEqualTo("PASSWORD_POLICY_VIOLATION");
    }

    @Test
    void rejectsPasswordsContainingTheUsername() {
        assertThatThrownBy(() -> service.validateNewPassword("I-am-Kofi.Mensah-77", "kofi.mensah", null))
                .isInstanceOf(BankingException.class);
    }

    @Test
    void rejectsReuseOfTheCurrentPassword() {
        String hash = service.hash("Correct-Horse-Battery-9");
        assertThatThrownBy(() -> service.validateNewPassword("Correct-Horse-Battery-9", "user", hash))
                .isInstanceOf(BankingException.class);
    }

    @Test
    void acceptsLongPassphrases() {
        assertThatCode(() -> service.validateNewPassword("Correct-Horse-Battery-9", "kofi.mensah", null))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validateNewPassword("tro-tro to Kaneshie at dawn", "kofi.mensah", null))
                .doesNotThrowAnyException();
    }

    @Test
    void temporaryPasswordsSatisfyThePolicyAndAreUnpredictable() {
        String first = service.generateTemporaryPassword();
        String second = service.generateTemporaryPassword();
        assertThat(first).hasSize(16).isNotEqualTo(second);
        assertThatCode(() -> service.validateNewPassword(first, "someone", null)).doesNotThrowAnyException();
    }

    @Test
    void hashesAreSaltedAndVerifiable() {
        String first = service.hash("Correct-Horse-Battery-9");
        String second = service.hash("Correct-Horse-Battery-9");
        assertThat(first).isNotEqualTo(second).doesNotContain("Correct-Horse");
        assertThat(service.matches("Correct-Horse-Battery-9", first)).isTrue();
        assertThat(service.matches("correct-horse-battery-9", first)).isFalse();
    }
}
