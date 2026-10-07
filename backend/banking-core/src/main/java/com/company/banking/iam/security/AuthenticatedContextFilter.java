package com.company.banking.iam.security;

import com.company.banking.common.error.ApiErrorWriter;
import com.company.banking.common.security.ActorAuthenticationToken;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.iam.exception.IamErrorCode;
import com.company.banking.iam.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs right after bearer-token authentication. Binds the tenant from the verified token (the only trusted source)
 * and rejects tokens whose session has been revoked or has expired, so logout and administrative revocation take
 * effect immediately rather than when the access token expires.
 */
@RequiredArgsConstructor
public class AuthenticatedContextFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final ApiErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof ActorAuthenticationToken token)) {
            chain.doFilter(request, response);
            return;
        }
        AuthenticatedActor actor = token.getPrincipal();
        TenantContext.bind(actor.tenantId());
        MDC.put("actorId", String.valueOf(actor.id()));
        MDC.put("tenantId", String.valueOf(actor.tenantId()));
        try {
            if (!sessionService.isActive(actor.sessionId())) {
                SecurityContextHolder.clearContext();
                errorWriter.write(response, IamErrorCode.SESSION_REVOKED);
                return;
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("actorId");
            MDC.remove("tenantId");
        }
    }
}
