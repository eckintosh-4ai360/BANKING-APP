package com.company.banking.iam.security.mfa;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /**
     * RFC 6238 Appendix B (SHA-1 secret "12345678901234567890").
     */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheRfc6238TestVectors() {
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(59)), 8)).isEqualTo("94287082");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(1111111109)), 8)).isEqualTo("07081804");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(1234567890)), 8)).isEqualTo("89005924");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(2000000000)), 8)).isEqualTo("69279037");
    }

    @Test
    void acceptsOneStepOfClockDriftButNotMore() {
        Instant now = Instant.ofEpochSecond(1_900_000_000L);
        long step = Totp.stepAt(now);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1, 6), now, null)).isEqualTo(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1, 6), now, null)).isEqualTo(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2, 6), now, null)).isEqualTo(-1);
    }

    @Test
    void aUsedCodeCannotBeReplayed() {
        Instant now = Instant.ofEpochSecond(1_900_000_000L);
        long step = Totp.stepAt(now);
        String code = Totp.code(RFC_SECRET, step, 6);
        assertThat(Totp.verify(RFC_SECRET, code, now, null)).isEqualTo(step);
        assertThat(Totp.verify(RFC_SECRET, code, now, step)).isEqualTo(-1);
    }

    @Test
    void rejectsMalformedCodes() {
        Instant now = Instant.now();
        assertThat(Totp.verify(RFC_SECRET, null, now, null)).isEqualTo(-1);
        assertThat(Totp.verify(RFC_SECRET, "12345", now, null)).isEqualTo(-1);
        assertThat(Totp.verify(RFC_SECRET, "abcdef", now, null)).isEqualTo(-1);
    }

    @Test
    void base32MatchesRfc4648() {
        assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(new String(Base32.decode("MZXW6YTBOI"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
        assertThat(new String(Base32.decode("mzxw 6ytb oi======"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
    }
}
