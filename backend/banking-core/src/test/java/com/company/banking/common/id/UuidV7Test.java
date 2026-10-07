package com.company.banking.common.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

    @Test
    void hasVersion7AndRfcVariant() {
        UUID id = UuidV7.next();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsTheTimestampSoIdsSortByCreationTime() {
        UUID earlier = UuidV7.at(1_700_000_000_000L);
        UUID later = UuidV7.at(1_700_000_000_001L);
        assertThat(earlier.getMostSignificantBits() >>> 16).isEqualTo(1_700_000_000_000L);
        assertThat(earlier.compareTo(later)).isNegative();
    }

    @Test
    void isUniqueUnderLoad() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            ids.add(UuidV7.next());
        }
        assertThat(ids).hasSize(100_000);
    }
}
