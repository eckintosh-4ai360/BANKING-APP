package com.company.banking.common.security;

import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class CurrentActor {

    private CurrentActor() {
    }

    public static Optional<AuthenticatedActor> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedActor actor) {
            return Optional.of(actor);
        }
        return Optional.empty();
    }

    public static AuthenticatedActor require() {
        return current().orElseThrow(() -> new BankingException(CommonErrorCode.UNAUTHENTICATED));
    }

    public static Optional<UUID> currentActorId() {
        return current().map(AuthenticatedActor::id);
    }

    /**
     * Runs {@code action} as the system actor (all branches, no permissions). Used by seeding and scheduled jobs;
     * authorization annotations still apply to any endpoint, which the system actor never calls.
     */
    public static <T> T callAsSystem(UUID tenantId, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new ActorAuthenticationToken(
                AuthenticatedActor.system(tenantId), null, AuthorityUtils.NO_AUTHORITIES));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
