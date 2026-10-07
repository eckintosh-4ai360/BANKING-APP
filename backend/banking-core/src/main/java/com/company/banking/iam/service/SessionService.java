package com.company.banking.iam.service;

import com.company.banking.common.id.UuidV7;
import com.company.banking.common.web.RequestMetadata;
import com.company.banking.iam.entity.AuthSession;
import com.company.banking.iam.entity.PrincipalType;
import com.company.banking.iam.entity.RefreshToken;
import com.company.banking.iam.repository.AuthSessionRepository;
import com.company.banking.iam.repository.RefreshTokenRepository;
import com.company.banking.iam.security.BankingSecurityProperties;
import com.company.banking.iam.security.RefreshTokenCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessions and single-use rotating refresh tokens.
 *
 * <p>Clients must refresh one request at a time ("single flight"): presenting an already-rotated refresh token is
 * treated as token theft and revokes the whole session.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_REUSE = "REFRESH_TOKEN_REUSE";
    public static final String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String REASON_ROLES_CHANGED = "ROLES_CHANGED";
    public static final String REASON_ACCESS_CHANGED = "ACCESS_CHANGED";
    public static final String REASON_ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String REASON_CREDENTIAL_RESET = "CREDENTIAL_RESET";
    public static final String REASON_TENANT_SUSPENDED = "TENANT_SUSPENDED";

    private final AuthSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCodec codec;
    private final BankingSecurityProperties properties;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public NewSession open(PrincipalType principalType, UUID principalId, UUID tenantId) {
        Instant now = clock.instant();
        RequestMetadata client = RequestMetadata.current();
        AuthSession session = sessionRepository.save(new AuthSession(UuidV7.next(), tenantId, principalType,
                principalId, now, now.plus(absoluteTtl(principalType)), client.ipAddress(), client.userAgent()));
        IssuedRefreshToken refreshToken = issueRefreshToken(session, now);
        return new NewSession(session.getId(), refreshToken.value(), refreshToken.expiresAt());
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId) {
        return sessionRepository.isActive(sessionId, clock.instant());
    }

    /**
     * Exchanges a refresh token for a new one. Never throws for bad tokens: rejections are returned so that a
     * reuse-triggered revocation commits with the surrounding transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RotationResult rotate(String rawToken) {
        Instant now = clock.instant();
        Optional<RefreshToken> found = refreshTokenRepository.lockByHash(codec.hash(rawToken));
        if (found.isEmpty()) {
            return new RotationResult.Rejected(false, null);
        }
        RefreshToken token = found.get();
        AuthSession session = sessionRepository.findById(token.getSessionId()).orElse(null);
        if (session == null) {
            return new RotationResult.Rejected(false, null);
        }
        if (token.isRotated()) {
            session.revoke(REASON_REUSE, now);
            sessionRepository.save(session);
            return new RotationResult.Rejected(true, session.getId());
        }
        if (token.isExpired(now) || !session.isUsable(now)) {
            return new RotationResult.Rejected(false, session.getId());
        }
        token.markRotated(now);
        refreshTokenRepository.save(token);
        session.markRefreshed(now);
        sessionRepository.save(session);
        IssuedRefreshToken next = issueRefreshToken(session, now);
        return new RotationResult.Rotated(session, next.value(), next.expiresAt());
    }

    @Transactional
    public void revoke(UUID sessionId, String reason) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.revoke(reason, clock.instant());
            sessionRepository.save(session);
        });
    }

    @Transactional
    public int revokeAllOfPrincipal(PrincipalType principalType, UUID principalId, String reason) {
        return sessionRepository.revokeAllOfPrincipal(principalType, principalId, reason, clock.instant());
    }

    /**
     * Must run in the tenant's context (row-level security).
     */
    @Transactional
    public int revokeAllOfTenant(UUID tenantId, String reason) {
        return sessionRepository.revokeAllOfTenant(tenantId, reason, clock.instant());
    }

    private IssuedRefreshToken issueRefreshToken(AuthSession session, Instant now) {
        String raw = codec.generate(session.getTenantId());
        Instant idleExpiry = now.plus(idleTtl(session.getPrincipalType()));
        Instant expiresAt = idleExpiry.isBefore(session.getExpiresAt()) ? idleExpiry : session.getExpiresAt();
        refreshTokenRepository.save(new RefreshToken(codec.hash(raw), session.getTenantId(), session.getId(), now,
                expiresAt));
        return new IssuedRefreshToken(raw, expiresAt);
    }

    private Duration absoluteTtl(PrincipalType type) {
        return type == PrincipalType.STAFF
                ? properties.session().staffAbsoluteTtl()
                : properties.session().platformAbsoluteTtl();
    }

    private Duration idleTtl(PrincipalType type) {
        return type == PrincipalType.STAFF
                ? properties.session().staffIdleTtl()
                : properties.session().platformIdleTtl();
    }

    public record NewSession(UUID sessionId, String refreshToken, Instant refreshTokenExpiresAt) {
    }

    private record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    public sealed interface RotationResult {

        record Rotated(AuthSession session, String refreshToken, Instant refreshTokenExpiresAt)
                implements RotationResult {
        }

        record Rejected(boolean reuseDetected, UUID sessionId) implements RotationResult {
        }
    }
}
