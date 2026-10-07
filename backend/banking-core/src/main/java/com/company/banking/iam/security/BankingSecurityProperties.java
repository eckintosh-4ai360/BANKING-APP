package com.company.banking.iam.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;
import java.util.List;

/**
 * {@code banking.security.*}. Defaults live in {@code application.yml}.
 */
@ConfigurationProperties("banking.security")
public record BankingSecurityProperties(
        Jwt jwt,
        Session session,
        Lockout lockout,
        RateLimit rateLimit,
        PasswordHashing passwordHashing,
        Mfa mfa,
        Cors cors) {

    /**
     * @param issuer           name shown in authenticator apps
     * @param platformRequired platform administrators must enrol an authenticator before using any permission
     * @param challengeTtl     how long the login MFA challenge stays valid
     */
    public record Mfa(String issuer, boolean platformRequired, Duration challengeTtl) {
    }

    /**
     * Argon2id cost. OWASP minimum: 19 MiB memory, 2 iterations, parallelism 1. Deployed environments refuse
     * anything weaker (see ProductionSafetyGuard); tests lower it for speed.
     */
    public record PasswordHashing(int memoryKib, int iterations, int parallelism) {

        public static final int MIN_MEMORY_KIB = 19 * 1024;
        public static final int MIN_ITERATIONS = 2;

        public boolean meetsMinimum() {
            return memoryKib >= MIN_MEMORY_KIB && iterations >= MIN_ITERATIONS && parallelism >= 1;
        }
    }

    /**
     * @param privateKey         PKCS#8 PEM RSA private key; required outside local/test
     * @param publicKey          X.509 PEM RSA public key matching {@code privateKey}
     * @param allowEphemeralKeys generate an in-memory key pair at startup when no keys are configured. Tokens then
     *                           don't survive restarts and can't be shared between instances, so this is for local
     *                           development and tests only.
     */
    public record Jwt(
            String issuer,
            Duration accessTokenTtl,
            Resource privateKey,
            Resource publicKey,
            String keyId,
            boolean allowEphemeralKeys) {
    }

    public record Session(
            Duration staffAbsoluteTtl,
            Duration staffIdleTtl,
            Duration platformAbsoluteTtl,
            Duration platformIdleTtl) {
    }

    public record Lockout(int maxFailedAttempts, Duration duration) {
    }

    /**
     * @param store {@code redis} (shared across instances) or {@code in-memory} (single instance / tests)
     */
    public record RateLimit(String store, Limit loginPerIp, Limit loginPerAccount) {

        public record Limit(int limit, Duration window) {
        }
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
