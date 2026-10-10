package com.company.banking.iam.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * RS256 signing and verification of access tokens. Asymmetric keys let other services (or a gateway) verify
 * tokens with the public key only, and keep the door open to an external identity provider later.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    static final Set<String> AUDIENCES = Set.of("staff", "platform", "customer");

    @Bean
    public RSAKey jwtSigningKey(BankingSecurityProperties properties) throws JOSEException {
        BankingSecurityProperties.Jwt jwt = properties.jwt();
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        if (jwt.privateKey() != null && jwt.publicKey() != null) {
            privateKey = read(jwt.privateKey(), true);
            publicKey = read(jwt.publicKey(), false);
        } else if (jwt.allowEphemeralKeys()) {
            log.warn("No JWT signing keys configured: generating an ephemeral RSA key pair. "
                    + "Tokens will not survive a restart. Never use this outside local development and tests.");
            KeyPair keyPair = generateKeyPair();
            publicKey = (RSAPublicKey) keyPair.getPublic();
            privateKey = (RSAPrivateKey) keyPair.getPrivate();
        } else {
            throw new IllegalStateException(
                    "JWT signing keys are not configured (banking.security.jwt.private-key / public-key)");
        }
        RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        String keyId = jwt.keyId() != null && !jwt.keyId().isBlank() ? jwt.keyId() : key.computeThumbprint().toString();
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey jwtSigningKey, BankingSecurityProperties properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)),
                new JwtIssuerValidator(properties.jwt().issuer()),
                JwtConfig::validateBankingClaims));
        return decoder;
    }

    /**
     * Exactly one known audience, a session id, a UUID subject and, for staff and customers, a tenant.
     */
    static OAuth2TokenValidatorResult validateBankingClaims(Jwt jwt) {
        List<String> audience = jwt.getAudience();
        if (audience == null || audience.size() != 1 || !AUDIENCES.contains(audience.getFirst())) {
            return invalid("Unexpected audience");
        }
        if (!isUuid(jwt.getSubject()) || !isUuid(jwt.getClaimAsString("sid"))) {
            return invalid("Missing subject or session");
        }
        if (!"platform".equals(audience.getFirst()) && !isUuid(jwt.getClaimAsString("tid"))) {
            return invalid("Missing tenant");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult invalid(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }

    private static boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(Resource resource, boolean privateKey) {
        try (InputStream in = resource.getInputStream()) {
            return (T) (privateKey ? RsaKeyConverters.pkcs8().convert(in) : RsaKeyConverters.x509().convert(in));
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read JWT key " + resource, ex);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
