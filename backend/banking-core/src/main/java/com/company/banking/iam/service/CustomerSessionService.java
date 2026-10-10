package com.company.banking.iam.service;

import com.company.banking.common.security.ActorType;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.iam.dto.SessionInfo;
import com.company.banking.iam.dto.TokenResponse;
import com.company.banking.iam.entity.AuthSession;
import com.company.banking.iam.entity.PrincipalType;
import com.company.banking.iam.repository.AuthSessionRepository;
import com.company.banking.iam.security.AccessTokenService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessions of customers, for the customer channel: a customer's session is bound to one of their trusted devices.
 * Customer tokens carry no permissions or branches: the customer API serves only the signed-in customer's own data.
 */
@Service
@RequiredArgsConstructor
public class CustomerSessionService {

    private final SessionService sessionService;
    private final AuthSessionRepository sessions;
    private final AccessTokenService accessTokenService;
    private final Clock clock;

    /**
     * Opens a session on a trusted device and issues its first tokens.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public TokenResponse open(UUID tenantId, UUID customerId, String username, UUID deviceId) {
        SessionService.NewSession session = sessionService.open(PrincipalType.CUSTOMER, customerId, tenantId,
                deviceId);
        return tokens(actor(tenantId, customerId, username, session.sessionId()), session.refreshToken(),
                session.refreshTokenExpiresAt());
    }

    @Transactional
    public int revokeAll(UUID customerId, String reason) {
        return sessionService.revokeAllOfPrincipal(PrincipalType.CUSTOMER, customerId, reason);
    }

    /**
     * Ends every session on one of the customer's devices (the device is no longer trusted).
     */
    @Transactional
    public int revokeDevice(UUID customerId, UUID deviceId, String reason) {
        return sessions.revokeAllOfDevice(PrincipalType.CUSTOMER, customerId, deviceId, reason, clock.instant());
    }

    /**
     * Ends one of the customer's own sessions.
     *
     * @return false when the customer has no such session
     */
    @Transactional
    public boolean revoke(UUID customerId, UUID sessionId, String reason) {
        return sessions.findById(sessionId)
                .filter(session -> session.getPrincipalType() == PrincipalType.CUSTOMER
                        && session.getPrincipalId().equals(customerId))
                .map(session -> {
                    session.revoke(reason, clock.instant());
                    sessions.save(session);
                    return true;
                })
                .orElse(false);
    }

    /**
     * The customer's latest sessions, newest first (their login history).
     */
    @Transactional(readOnly = true)
    public List<SessionInfo> recent(UUID customerId, int limit) {
        UUID current = CurrentActor.current().map(AuthenticatedActor::sessionId).orElse(null);
        return sessions.findByPrincipalTypeAndPrincipalIdOrderByCreatedAtDesc(PrincipalType.CUSTOMER, customerId,
                        Limit.of(limit)).stream()
                .map(session -> toInfo(session, current))
                .toList();
    }

    static AuthenticatedActor actor(UUID tenantId, UUID customerId, String username, UUID sessionId) {
        return new AuthenticatedActor(ActorType.CUSTOMER, customerId, tenantId, username, sessionId, Set.of(),
                BranchScope.none(), false, false);
    }

    TokenResponse tokens(AuthenticatedActor actor, String refreshToken, Instant refreshTokenExpiresAt) {
        AccessTokenService.IssuedAccessToken access = accessTokenService.issue(actor, PrincipalType.CUSTOMER);
        return TokenResponse.bearer(access.value(), access.expiresAt(), refreshToken, refreshTokenExpiresAt, false,
                false);
    }

    private static SessionInfo toInfo(AuthSession session, UUID current) {
        return new SessionInfo(session.getId(), session.getDeviceId(), session.getStatus().name(),
                session.getCreatedAt(), session.getLastRefreshedAt(), session.getExpiresAt(), session.getRevokedAt(),
                session.getRevokeReason(), session.getIpAddress(), session.getUserAgent(),
                session.getId().equals(current));
    }
}
