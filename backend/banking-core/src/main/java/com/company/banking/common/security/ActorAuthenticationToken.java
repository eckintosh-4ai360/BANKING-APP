package com.company.banking.common.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Spring Security authentication carrying an {@link AuthenticatedActor}. Authorities are the actor's permission
 * codes plus an audience marker ({@code AUD_staff}, {@code AUD_platform}).
 */
public class ActorAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedActor actor;
    private final transient Object credentials;

    public ActorAuthenticationToken(AuthenticatedActor actor, Object credentials,
                                    Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.actor = actor;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedActor getPrincipal() {
        return actor;
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public String getName() {
        return actor.username();
    }
}
