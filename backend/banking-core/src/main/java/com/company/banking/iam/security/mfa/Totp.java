package com.company.banking.iam.security.mfa;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * RFC 6238 time-based one-time passwords: HMAC-SHA1, 30-second steps, 6 digits, accepting one step of clock drift
 * either way. A code is accepted only for a step later than the last accepted one, so an intercepted code cannot be
 * replayed.
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30;
    private static final int DRIFT_STEPS = 1;
    private static final int[] POWERS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

    private Totp() {
    }

    public static long stepAt(Instant instant) {
        return instant.getEpochSecond() / STEP_SECONDS;
    }

    public static String code(byte[] secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return String.format("%0" + digits + "d", binary % POWERS[digits]);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("TOTP computation failed", ex);
        }
    }

    /**
     * @param lastUsedStep the step of the last accepted code, or {@code null}
     * @return the matching step, or {@code -1} when the code is wrong, expired or already used
     */
    public static long verify(byte[] secret, String code, Instant now, Long lastUsedStep) {
        if (code == null || !code.matches("^[0-9]{" + DIGITS + "}$")) {
            return -1;
        }
        long current = stepAt(now);
        long matched = -1;
        for (long step = current - DRIFT_STEPS; step <= current + DRIFT_STEPS; step++) {
            boolean equal = MessageDigest.isEqual(code(secret, step, DIGITS).getBytes(), code.getBytes());
            if (equal && (lastUsedStep == null || step > lastUsedStep)) {
                matched = step;
            }
        }
        return matched;
    }
}
