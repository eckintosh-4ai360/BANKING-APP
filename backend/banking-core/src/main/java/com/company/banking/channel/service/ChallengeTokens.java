package com.company.banking.channel.service;

import java.util.Optional;
import java.util.UUID;

/**
 * The opaque handle a client gets for a texted code: {@code <tenant id>.<challenge id>}. The tenant part lets the
 * server find the challenge before anyone is signed in; knowing a handle is useless without the code.
 */
final class ChallengeTokens {

    record Parsed(UUID tenantId, UUID challengeId) {
    }

    private ChallengeTokens() {
    }

    static String format(UUID tenantId, UUID challengeId) {
        return tenantId + "." + challengeId;
    }

    static Optional<Parsed> parse(String token) {
        if (token == null || token.length() != 73) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Parsed(UUID.fromString(parts[0]), UUID.fromString(parts[1])));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
