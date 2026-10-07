package com.company.banking.common.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * RFC 9562 version 7 UUIDs: 48-bit Unix millisecond timestamp followed by random bits. Time ordering keeps
 * B-tree inserts append-mostly, which matters for high-volume tables such as journals and audit logs.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID next() {
        return at(System.currentTimeMillis());
    }

    static UUID at(long epochMillis) {
        long randA = RANDOM.nextInt(1 << 12);
        long mostSignificant = (epochMillis << 16) | (0x7L << 12) | randA;
        long leastSignificant = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
