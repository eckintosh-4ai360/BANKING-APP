package com.company.banking.iam.security;

import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.BranchScope;
import com.company.banking.iam.entity.PrincipalType;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Issues RS256 access tokens. Claims: {@code sub} actor id, {@code aud} staff|platform, {@code tid} tenant,
 * {@code sid} session, {@code perms} permission codes, {@code brs} branch scope ("*" or ids), {@code pcr}
 * password change required, {@code uname} username.
 */
@Service
@RequiredArgsConstructor
public class AccessTokenService {

    public static final String CLAIM_TENANT = "tid";
    public static final String CLAIM_SESSION = "sid";
    public static final String CLAIM_PERMISSIONS = "perms";
    public static final String CLAIM_BRANCHES = "brs";
    public static final String CLAIM_PASSWORD_CHANGE = "pcr";
    public static final String CLAIM_USERNAME = "uname";
    public static final String CLAIM_MFA_ENROLLMENT = "mer";
    public static final String ALL_BRANCHES = "*";

    private final JwtEncoder jwtEncoder;
    private final RSAKey jwtSigningKey;
    private final BankingSecurityProperties properties;
    private final Clock clock;

    public IssuedAccessToken issue(AuthenticatedActor actor, PrincipalType principalType) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().accessTokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(actor.id().toString())
                .audience(List.of(principalType.audience()))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_SESSION, actor.sessionId().toString())
                .claim(CLAIM_USERNAME, actor.username())
                .claim(CLAIM_PERMISSIONS, actor.permissions().stream().sorted().toList())
                .claim(CLAIM_BRANCHES, branchClaim(actor.branchScope()))
                .claim(CLAIM_PASSWORD_CHANGE, actor.passwordChangeRequired())
                .claim(CLAIM_MFA_ENROLLMENT, actor.mfaEnrollmentRequired());
        if (actor.tenantId() != null) {
            claims.claim(CLAIM_TENANT, actor.tenantId().toString());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwtSigningKey.getKeyID()).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }

    private static Object branchClaim(BranchScope scope) {
        if (scope.allBranches()) {
            return ALL_BRANCHES;
        }
        return scope.branchIds().stream().map(UUID::toString).sorted().toList();
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
