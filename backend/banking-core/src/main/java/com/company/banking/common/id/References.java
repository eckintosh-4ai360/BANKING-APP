package com.company.banking.common.id;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Human-readable references for high-volume records (journals, transactions): {@code PREFIX-YYYYMMDD-XXXXXXXXXXXX}.
 *
 * <p>The suffix is 60 random bits in Crockford base32 rather than a counter: a gap-free counter would serialize
 * every posting of an institution on one row. Uniqueness is guaranteed by a unique constraint: at a million postings
 * a day a clash is expected about once in several thousand years, and it fails that posting rather than corrupting
 * anything (an idempotent retry then succeeds).
 */
public final class References {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SUFFIX_LENGTH = 12;

    private References() {
    }

    public static String next(String prefix, LocalDate date) {
        long bits = RANDOM.nextLong();
        char[] suffix = new char[SUFFIX_LENGTH];
        for (int index = SUFFIX_LENGTH - 1; index >= 0; index--) {
            suffix[index] = ALPHABET[(int) (bits & 31)];
            bits >>>= 5;
        }
        return prefix + "-" + date.format(DATE) + "-" + new String(suffix);
    }
}
