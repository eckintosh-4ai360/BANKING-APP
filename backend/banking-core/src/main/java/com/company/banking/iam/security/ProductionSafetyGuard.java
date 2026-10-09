package com.company.banking.iam.security;

import com.company.banking.audit.service.AuditSealProperties;
import com.company.banking.common.crypto.CryptoProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Refuses to start production, staging or development deployments with settings meant only for a laptop.
 */
@Component
@Profile({"prod", "staging", "dev"})
public class ProductionSafetyGuard implements InitializingBean {

    /**
     * Encryption keys committed to the repository for local development and tests.
     */
    private static final Set<String> PUBLISHED_KEYS = Set.of(
            "bN9YtA/MrxiGo85v+XFsh9dIl9SEyvMbkA+lTekXVT8=",
            "Ing4I/mBG69mjiX86y9NBzQU8XvLiRHTu8hWM/nr6U4=",
            "BVOxNmlXHutcXPq9VPpsu97iDGWPvjp6GCFAdnrmrwM=",
            "ACtTDQ1zY8e+jYoxRHFUOd4MVP7IIIBM+9shtcgHaDc=",
            "U+Y9NROLhm0a8O19L9xty26psAg5AIHHazP5V1frC7U=",
            "MW2Iz7lJP0DfwbNND8KV6wgowJ3LR+SIgnZOSP0nT64=");

    private final BankingSecurityProperties properties;
    private final CryptoProperties cryptoProperties;
    private final AuditSealProperties sealProperties;
    private final Environment environment;

    public ProductionSafetyGuard(BankingSecurityProperties properties, CryptoProperties cryptoProperties,
                                 AuditSealProperties sealProperties, Environment environment) {
        this.properties = properties;
        this.cryptoProperties = cryptoProperties;
        this.sealProperties = sealProperties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (Arrays.asList(environment.getActiveProfiles()).contains("local")) {
            fail("The 'local' profile cannot be combined with a deployed environment");
        }
        if (properties.jwt().allowEphemeralKeys()) {
            fail("Ephemeral JWT keys are not allowed in deployed environments");
        }
        if (!"redis".equals(properties.rateLimit().store())) {
            fail("Deployed environments must use the shared (redis) rate limiter");
        }
        if (!properties.passwordHashing().meetsMinimum()) {
            fail("Password hashing cost is below the OWASP Argon2id minimum");
        }
        if (!properties.mfa().platformRequired()) {
            fail("Platform administrators must be required to use MFA in deployed environments");
        }
        boolean publishedKey = PUBLISHED_KEYS.contains(cryptoProperties.blindIndexKey())
                || cryptoProperties.dataKeys().values().stream().anyMatch(PUBLISHED_KEYS::contains)
                || sealProperties.keys().values().stream().anyMatch(PUBLISHED_KEYS::contains);
        if (publishedKey) {
            fail("A published development encryption key is configured");
        }
        if ("stub".equals(environment.getProperty("banking.kyc.identity-verification-provider"))) {
            fail("The stub identity-verification provider is not allowed in deployed environments");
        }
    }

    private static void fail(String reason) {
        throw new IllegalStateException(reason);
    }
}
