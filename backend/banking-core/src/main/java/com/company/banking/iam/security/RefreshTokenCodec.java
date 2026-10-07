package com.company.banking.iam.security;

import com.company.banking.iam.entity.PrincipalType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Opaque refresh tokens: {@code s.<tenantId>.<secret>} for staff and {@code p.<secret>} for platform users, where
 * the secret is 256 random bits (base64url). The tenant prefix lets a refresh request run inside the right tenant
 * context before any lookup; it grants nothing by itself because only the SHA-256 hash of the full token is stored
 * and matched.
 */
@Component
public class RefreshTokenCodec {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern SECRET = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final int MAX_LENGTH = 128;

    public String generate(UUID tenantId) {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return tenantId == null ? "p." + encoded : "s." + tenantId + "." + encoded;
    }

    public Optional<ParsedRefreshToken> parse(String token) {
        if (token == null || token.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length == 2 && "p".equals(parts[0]) && SECRET.matcher(parts[1]).matches()) {
            return Optional.of(new ParsedRefreshToken(PrincipalType.PLATFORM, null));
        }
        if (parts.length == 3 && "s".equals(parts[0]) && SECRET.matcher(parts[2]).matches()) {
            try {
                return Optional.of(new ParsedRefreshToken(PrincipalType.STAFF, UUID.fromString(parts[1])));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record ParsedRefreshToken(PrincipalType principalType, UUID tenantId) {
    }
}
