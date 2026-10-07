package com.company.banking.iam.security.mfa;

import com.company.banking.iam.entity.PrincipalType;
import com.company.banking.iam.security.BankingSecurityProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The short-lived, signed proof that a password check passed and an authenticator code is still owed. Its audience
 * ({@code mfa}) is rejected by the access-token decoder, so it can never be used to call the API.
 */
@Component
public class MfaChallengeService {

    static final String AUDIENCE = "mfa";
    private static final String CLAIM_PRINCIPAL_TYPE = "ptype";
    private static final String CLAIM_TENANT = "tid";

    private final JwtEncoder jwtEncoder;
    private final RSAKey signingKey;
    private final BankingSecurityProperties properties;
    private final Clock clock;
    private final NimbusJwtDecoder decoder;

    public MfaChallengeService(JwtEncoder jwtEncoder, RSAKey jwtSigningKey, BankingSecurityProperties properties,
                               Clock clock) throws JOSEException {
        this.jwtEncoder = jwtEncoder;
        this.signingKey = jwtSigningKey;
        this.properties = properties;
        this.clock = clock;
        this.decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)),
                new JwtIssuerValidator(properties.jwt().issuer()),
                MfaChallengeService::validateAudience));
    }

    public Issued issue(PrincipalType principalType, UUID principalId, UUID tenantId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.mfa().challengeTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(principalId.toString())
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_PRINCIPAL_TYPE, principalType.name());
        if (tenantId != null) {
            claims.claim(CLAIM_TENANT, tenantId.toString());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
        return new Issued(jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue(),
                expiresAt);
    }

    public Optional<Challenge> parse(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            String tenant = jwt.getClaimAsString(CLAIM_TENANT);
            return Optional.of(new Challenge(PrincipalType.valueOf(jwt.getClaimAsString(CLAIM_PRINCIPAL_TYPE)),
                    UUID.fromString(jwt.getSubject()), tenant == null ? null : UUID.fromString(tenant)));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
    }

    private static OAuth2TokenValidatorResult validateAudience(Jwt jwt) {
        return jwt.getAudience() != null && jwt.getAudience().equals(List.of(AUDIENCE))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not an MFA challenge", null));
    }

    public record Issued(String token, Instant expiresAt) {
    }

    public record Challenge(PrincipalType principalType, UUID principalId, UUID tenantId) {
    }
}
