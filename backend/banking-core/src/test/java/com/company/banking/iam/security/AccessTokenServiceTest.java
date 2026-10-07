package com.company.banking.iam.security;

import com.company.banking.common.security.ActorType;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.BranchScope;
import com.company.banking.iam.entity.PrincipalType;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenServiceTest {

    private final BankingSecurityProperties properties = new BankingSecurityProperties(
            new BankingSecurityProperties.Jwt("banking-core", Duration.ofMinutes(10), null, null, null, true),
            null, null, null, null, null, null);
    private final JwtConfig config = new JwtConfig();

    @Test
    void issuedTokensRoundTripIntoTheSameActor() throws Exception {
        RSAKey key = config.jwtSigningKey(properties);
        AccessTokenService service = new AccessTokenService(config.jwtEncoder(key), key, properties,
                Clock.systemUTC());
        JwtDecoder decoder = config.jwtDecoder(key, properties);
        UUID branch = UUID.randomUUID();
        AuthenticatedActor actor = new AuthenticatedActor(ActorType.STAFF, UUID.randomUUID(), UUID.randomUUID(),
                "kofi", UUID.randomUUID(), Set.of("branch.view", "staff.view"), BranchScope.of(Set.of(branch)),
                false, false);

        String token = service.issue(actor, PrincipalType.STAFF).value();
        var authentication = new ActorJwtAuthenticationConverter().convert(decoder.decode(token));

        assertThat(authentication.getPrincipal()).isEqualTo(actor);
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("branch.view", "staff.view", "AUD_staff");
    }

    @Test
    void tokensSignedByAnotherKeyAreRejected() throws Exception {
        RSAKey issuerKey = config.jwtSigningKey(properties);
        RSAKey otherKey = config.jwtSigningKey(properties);
        AccessTokenService service = new AccessTokenService(config.jwtEncoder(issuerKey), issuerKey, properties,
                Clock.systemUTC());
        String token = service.issue(platformActor(), PrincipalType.PLATFORM).value();

        assertThatThrownBy(() -> config.jwtDecoder(otherKey, properties).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokensAreRejected() throws Exception {
        RSAKey key = config.jwtSigningKey(properties);
        Clock anHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        AccessTokenService service = new AccessTokenService(config.jwtEncoder(key), key, properties, anHourAgo);
        String token = service.issue(platformActor(), PrincipalType.PLATFORM).value();

        assertThatThrownBy(() -> config.jwtDecoder(key, properties).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void staffTokensWithoutTenantAreRejected() throws Exception {
        RSAKey key = config.jwtSigningKey(properties);
        AccessTokenService service = new AccessTokenService(config.jwtEncoder(key), key, properties,
                Clock.systemUTC());
        AuthenticatedActor tenantless = new AuthenticatedActor(ActorType.STAFF, UUID.randomUUID(), null, "x",
                UUID.randomUUID(), Set.of(), BranchScope.none(), false, false);
        String token = service.issue(tenantless, PrincipalType.STAFF).value();

        assertThatThrownBy(() -> config.jwtDecoder(key, properties).decode(token)).isInstanceOf(JwtException.class);
    }

    private static AuthenticatedActor platformActor() {
        return new AuthenticatedActor(ActorType.PLATFORM_ADMIN, UUID.randomUUID(), null, "owner", UUID.randomUUID(),
                Set.copyOf(List.of("platform.tenant.view")), BranchScope.none(), false, false);
    }
}
