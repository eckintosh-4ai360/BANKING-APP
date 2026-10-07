package com.company.banking.iam.security;

import com.company.banking.common.security.ActorAuthenticationToken;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.BranchScope;
import com.company.banking.iam.entity.PrincipalType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns a verified access token into an {@link AuthenticatedActor}. Authorities are the permission codes plus
 * {@code AUD_<audience>}, which URL rules use to keep each audience in its own API area.
 */
public class ActorJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String AUDIENCE_AUTHORITY_PREFIX = "AUD_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String audience = jwt.getAudience().getFirst();
        PrincipalType principalType = PrincipalType.fromAudience(audience);
        String tenantClaim = jwt.getClaimAsString(AccessTokenService.CLAIM_TENANT);
        List<String> permissions = jwt.getClaimAsStringList(AccessTokenService.CLAIM_PERMISSIONS);
        Set<String> permissionSet = permissions == null ? Set.of() : Set.copyOf(permissions);

        AuthenticatedActor actor = new AuthenticatedActor(
                principalType.actorType(),
                UUID.fromString(jwt.getSubject()),
                tenantClaim == null ? null : UUID.fromString(tenantClaim),
                jwt.getClaimAsString(AccessTokenService.CLAIM_USERNAME),
                UUID.fromString(jwt.getClaimAsString(AccessTokenService.CLAIM_SESSION)),
                permissionSet,
                branchScope(jwt.getClaims().get(AccessTokenService.CLAIM_BRANCHES)),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(AccessTokenService.CLAIM_PASSWORD_CHANGE)),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(AccessTokenService.CLAIM_MFA_ENROLLMENT)));

        Collection<GrantedAuthority> authorities = new ArrayList<>();
        permissionSet.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        authorities.add(new SimpleGrantedAuthority(AUDIENCE_AUTHORITY_PREFIX + audience));
        return new ActorAuthenticationToken(actor, jwt, authorities);
    }

    private static BranchScope branchScope(Object claim) {
        if (AccessTokenService.ALL_BRANCHES.equals(claim)) {
            return BranchScope.all();
        }
        if (claim instanceof Collection<?> ids) {
            return BranchScope.of(ids.stream().map(Object::toString).map(UUID::fromString).collect(Collectors.toSet()));
        }
        return BranchScope.none();
    }
}
