package com.company.banking.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditSealKeysTest {

    private static final String KEY_1 = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String KEY_2 = Base64.getEncoder().encodeToString("k".repeat(32).getBytes());
    private static final String HASH = "cd".repeat(32);

    @Test
    void signsWithTheActiveKeyAndChecksWithTheSealsOwnVersion() {
        AuditSealKeys rotated = keys(2, Map.of(1, KEY_1, 2, KEY_2));
        AuditSealKeys original = keys(1, Map.of(1, KEY_1));

        String oldSignature = original.sign(HASH);
        String newSignature = rotated.sign(HASH);
        assertThat(rotated.activeVersion()).isEqualTo(2);
        assertThat(newSignature).matches("^[0-9a-f]{64}$").isNotEqualTo(oldSignature);
        assertThat(rotated.verify(1, HASH, oldSignature)).contains(true);
        assertThat(rotated.verify(2, HASH, newSignature)).contains(true);
        assertThat(rotated.verify(2, HASH, oldSignature)).contains(false);
        assertThat(rotated.verify(2, "ef".repeat(32), newSignature)).contains(false);
        assertThat(original.verify(2, HASH, newSignature)).as("retired key").isEmpty();
    }

    @Test
    void refusesMissingShortOrInactiveKeys() {
        assertThatThrownBy(() -> keys(1, Map.of())).hasMessageContaining("No audit seal keys");
        assertThatThrownBy(() -> keys(1, Map.of(1, ""))).hasMessageContaining("Missing audit seal key v1");
        assertThatThrownBy(() -> keys(1, Map.of(1, Base64.getEncoder().encodeToString(new byte[16]))))
                .hasMessageContaining("at least 256 bits");
        assertThatThrownBy(() -> keys(2, Map.of(1, KEY_1))).hasMessageContaining("version 2 is not configured");
    }

    private static AuditSealKeys keys(int active, Map<Integer, String> keys) {
        return new AuditSealKeys(new AuditSealProperties(active, keys, Duration.ofHours(1), Duration.ofMinutes(5),
                168));
    }
}
