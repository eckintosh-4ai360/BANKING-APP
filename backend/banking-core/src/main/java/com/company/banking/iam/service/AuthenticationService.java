package com.company.banking.iam.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditOutcome;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.crypto.FieldEncryptionService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ErrorCode;
import com.company.banking.common.security.ActorType;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.iam.dto.ChangePasswordRequest;
import com.company.banking.iam.dto.MfaSetupResponse;
import com.company.banking.iam.dto.MfaVerifyRequest;
import com.company.banking.iam.dto.PlatformLoginRequest;
import com.company.banking.iam.dto.StaffLoginRequest;
import com.company.banking.iam.dto.TokenResponse;
import com.company.banking.iam.entity.AuthSession;
import com.company.banking.iam.entity.LoginCredential;
import com.company.banking.iam.entity.PlatformUser;
import com.company.banking.iam.entity.PrincipalType;
import com.company.banking.iam.entity.StaffCredential;
import com.company.banking.iam.exception.IamErrorCode;
import com.company.banking.iam.repository.PlatformUserRepository;
import com.company.banking.iam.repository.RoleRepository;
import com.company.banking.iam.repository.StaffCredentialRepository;
import com.company.banking.iam.security.AccessTokenService;
import com.company.banking.iam.security.BankingSecurityProperties;
import com.company.banking.iam.security.RefreshTokenCodec;
import com.company.banking.iam.security.RefreshTokenCodec.ParsedRefreshToken;
import com.company.banking.iam.security.mfa.Base32;
import com.company.banking.iam.security.mfa.MfaChallengeService;
import com.company.banking.iam.security.mfa.Totp;
import com.company.banking.iam.service.SessionService.NewSession;
import com.company.banking.iam.service.SessionService.RotationResult;
import com.company.banking.iam.spi.StaffDirectory;
import com.company.banking.iam.spi.StaffDirectory.StaffAuthProfile;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Login, multi-factor verification, token refresh, logout, password change and authenticator enrollment for staff
 * and platform administrators.
 *
 * <p>Credential checks run in a transaction that <b>commits even when the attempt fails</b>: the outcome is
 * returned as a value and only converted to an exception afterwards, so failed-attempt counters, lockouts and
 * security audit records are never rolled back.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final String AUTH_RESOURCE = "AUTHENTICATION";
    private static final int MFA_SECRET_BYTES = 20;

    private final TenantService tenantService;
    private final StaffCredentialRepository staffCredentialRepository;
    private final PlatformUserRepository platformUserRepository;
    private final RoleRepository roleRepository;
    private final StaffDirectory staffDirectory;
    private final SessionService sessionService;
    private final AccessTokenService accessTokenService;
    private final MfaChallengeService mfaChallengeService;
    private final PasswordService passwordService;
    private final LoginRateLimiter loginRateLimiter;
    private final RefreshTokenCodec refreshTokenCodec;
    private final FieldEncryptionService encryption;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final BankingSecurityProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TokenResponse loginStaff(StaffLoginRequest request) {
        String tenantCode = normalize(request.tenantCode());
        String username = normalize(request.username());
        loginRateLimiter.check("staff:" + tenantCode + ":" + username);

        Optional<TenantSummary> tenant = tenantService.findByCode(tenantCode);
        if (tenant.isEmpty()) {
            passwordService.burnDummyCheck(request.password());
            auditService.recordIndependently(loginFailure(ActorType.STAFF, null, username, "UNKNOWN_INSTITUTION")
                    .metadata("tenantCode", tenantCode)
                    .build());
            throw new BankingException(IamErrorCode.INVALID_CREDENTIALS);
        }
        TenantSummary institution = tenant.get();
        AuthOutcome outcome = TenantContext.callAs(institution.id(), () -> transactionTemplate.execute(
                status -> attemptStaffLogin(institution, username, request.password())));
        return outcome.tokensOrThrow();
    }

    public TokenResponse loginPlatform(PlatformLoginRequest request) {
        String username = normalize(request.username());
        loginRateLimiter.check("platform:" + username);
        AuthOutcome outcome = TenantContext.callAs(null, () -> transactionTemplate.execute(
                status -> attemptPlatformLogin(username, request.password())));
        return outcome.tokensOrThrow();
    }

    /**
     * Second login step for accounts with an authenticator.
     */
    public TokenResponse verifyMfa(MfaVerifyRequest request) {
        MfaChallengeService.Challenge challenge = mfaChallengeService.parse(request.challengeToken())
                .orElseThrow(() -> new BankingException(IamErrorCode.INVALID_MFA_CHALLENGE));
        loginRateLimiter.check("mfa:" + challenge.principalId());
        AuthOutcome outcome = TenantContext.callAs(challenge.tenantId(), () -> transactionTemplate.execute(
                status -> attemptMfa(challenge, request.code())));
        return outcome.tokensOrThrow();
    }

    public TokenResponse refresh(String refreshToken) {
        ParsedRefreshToken parsed = refreshTokenCodec.parse(refreshToken)
                .orElseThrow(() -> new BankingException(IamErrorCode.INVALID_REFRESH_TOKEN));
        AuthOutcome outcome = TenantContext.callAs(parsed.tenantId(), () -> transactionTemplate.execute(
                status -> attemptRefresh(refreshToken, parsed)));
        return outcome.tokensOrThrow();
    }

    @Transactional
    public void logout() {
        AuthenticatedActor actor = CurrentActor.require();
        sessionService.revoke(actor.sessionId(), SessionService.REASON_LOGOUT);
        auditService.record(AuditEvent.builder("LOGOUT", AUTH_RESOURCE).resourceId(actor.sessionId()).build());
    }

    public TokenResponse changePassword(ChangePasswordRequest request) {
        AuthenticatedActor actor = CurrentActor.require();
        AuthOutcome outcome = transactionTemplate.execute(status -> attemptPasswordChange(actor, request));
        return outcome.tokensOrThrow();
    }

    /**
     * Starts (or restarts) authenticator enrollment. The secret only takes effect once confirmed with a code.
     */
    @Transactional
    public MfaSetupResponse setupMfa() {
        AuthenticatedActor actor = CurrentActor.require();
        LoginCredential credential = lockCredentialOf(actor);
        if (credential.isMfaEnabled()) {
            throw new BankingException(IamErrorCode.MFA_ALREADY_ENABLED);
        }
        byte[] secret = new byte[MFA_SECRET_BYTES];
        random.nextBytes(secret);
        String encoded = Base32.encode(secret);
        credential.startMfaEnrollment(encryption.encrypt(encoded, mfaContext(actor)));
        save(credential);
        auditService.record(AuditEvent.builder("MFA_ENROLLMENT_STARTED", AUTH_RESOURCE).resourceId(actor.id())
                .build());
        String issuer = properties.mfa().issuer();
        String account = actor.type() == ActorType.STAFF
                ? credential.getUsername() + "@" + tenantService.getCurrent().code()
                : credential.getUsername();
        String uri = "otpauth://totp/" + urlEncode(issuer) + ":" + urlEncode(account) + "?secret=" + encoded
                + "&issuer=" + urlEncode(issuer) + "&algorithm=SHA1&digits=" + Totp.DIGITS
                + "&period=" + Totp.STEP_SECONDS;
        return new MfaSetupResponse(encoded, uri);
    }

    /**
     * Confirms enrollment with a first code, ends every other session and returns fresh, unrestricted tokens.
     */
    public TokenResponse activateMfa(String code) {
        AuthenticatedActor actor = CurrentActor.require();
        AuthOutcome outcome = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            LoginCredential credential = lockCredentialOf(actor);
            if (credential.isMfaEnabled()) {
                throw new BankingException(IamErrorCode.MFA_ALREADY_ENABLED);
            }
            if (credential.getMfaSecretEncrypted() == null) {
                throw new BankingException(IamErrorCode.MFA_NOT_STARTED);
            }
            long step = Totp.verify(mfaSecret(credential, actor.tenantId(), actor.id(), actor.type()), code, now,
                    null);
            if (step < 0) {
                return new AuthOutcome.Failure(IamErrorCode.INVALID_MFA_CODE);
            }
            credential.enableMfa(step, now);
            save(credential);
            PrincipalType principalType = principalTypeOf(actor);
            sessionService.revokeAllOfPrincipal(principalType, actor.id(), SessionService.REASON_ACCESS_CHANGED);
            NewSession session = sessionService.open(principalType, actor.id(), actor.tenantId());
            AuthenticatedActor refreshed = principalType == PrincipalType.STAFF
                    ? staffActor(actor.tenantId(), staffCredentialRepository.findByTenantIdAndStaffId(
                    actor.tenantId(), actor.id()).orElseThrow(), actor.branchScope(), session.sessionId())
                    : platformActor(platformUserRepository.findById(actor.id()).orElseThrow(), session.sessionId());
            auditService.record(AuditEvent.builder("MFA_ENABLED", AUTH_RESOURCE).resourceId(actor.id()).build());
            return new AuthOutcome.Success(tokens(refreshed, principalType, session.refreshToken(),
                    session.refreshTokenExpiresAt()));
        });
        return outcome.tokensOrThrow();
    }

    // ------------------------------------------------------------------------------------------------- Staff

    private AuthOutcome attemptStaffLogin(TenantSummary tenant, String username, String password) {
        Instant now = clock.instant();
        Optional<StaffCredential> found = staffCredentialRepository.lockByUsername(tenant.id(), username);
        if (found.isEmpty()) {
            passwordService.burnDummyCheck(password);
            auditService.record(loginFailure(ActorType.STAFF, null, username, "UNKNOWN_USER").build());
            return new AuthOutcome.Failure(IamErrorCode.INVALID_CREDENTIALS);
        }
        StaffCredential credential = found.get();
        AuthOutcome.Failure failure = verifyPassword(credential, password, now, ActorType.STAFF,
                credential.getStaffId());
        if (failure != null) {
            staffCredentialRepository.save(credential);
            return failure;
        }
        Optional<StaffAuthProfile> profile = activeStaff(tenant, credential);
        if (profile.isEmpty()) {
            return new AuthOutcome.Failure(IamErrorCode.ACCOUNT_DISABLED);
        }
        if (credential.isMfaEnabled()) {
            return mfaChallenge(PrincipalType.STAFF, credential.getStaffId(), tenant.id(), username);
        }
        return completeStaffLogin(tenant.id(), credential, profile.get(), now, false);
    }

    /**
     * The account and institution can sign in: credential enabled, institution active, staff member active.
     */
    private Optional<StaffAuthProfile> activeStaff(TenantSummary tenant, StaffCredential credential) {
        if (!tenant.isActive()) {
            auditService.record(loginFailure(ActorType.STAFF, credential.getStaffId(), credential.getUsername(),
                    "INSTITUTION_NOT_ACTIVE").build());
            return Optional.empty();
        }
        Optional<StaffAuthProfile> profile = staffDirectory.findAuthProfile(credential.getStaffId());
        if (!credential.isLoginEnabled() || profile.isEmpty() || !profile.get().isActive()) {
            auditService.record(loginFailure(ActorType.STAFF, credential.getStaffId(), credential.getUsername(),
                    "ACCOUNT_DISABLED").build());
            return Optional.empty();
        }
        return profile;
    }

    private AuthOutcome completeStaffLogin(UUID tenantId, StaffCredential credential, StaffAuthProfile profile,
                                           Instant now, boolean viaMfa) {
        credential.registerSuccessfulLogin(now);
        staffCredentialRepository.save(credential);
        NewSession session = sessionService.open(PrincipalType.STAFF, credential.getStaffId(), tenantId);
        AuthenticatedActor actor = staffActor(tenantId, credential, profile.branchScope(), session.sessionId());
        auditService.record(AuditEvent.builder("LOGIN_SUCCEEDED", AUTH_RESOURCE)
                .actor(ActorType.STAFF, actor.id(), actor.username())
                .resourceId(session.sessionId())
                .metadata("mfa", viaMfa)
                .build());
        return new AuthOutcome.Success(tokens(actor, PrincipalType.STAFF, session.refreshToken(),
                session.refreshTokenExpiresAt()));
    }

    private Optional<AuthenticatedActor> staffActorForSession(AuthSession session) {
        UUID tenantId = session.getTenantId();
        Optional<StaffCredential> credential = staffCredentialRepository.findByTenantIdAndStaffId(tenantId,
                session.getPrincipalId());
        boolean tenantActive = tenantService.findById(tenantId).map(TenantSummary::isActive).orElse(false);
        Optional<StaffAuthProfile> profile = staffDirectory.findAuthProfile(session.getPrincipalId());
        if (credential.isEmpty() || !credential.get().isLoginEnabled() || !tenantActive
                || profile.isEmpty() || !profile.get().isActive()) {
            return Optional.empty();
        }
        return Optional.of(staffActor(tenantId, credential.get(), profile.get().branchScope(), session.getId()));
    }

    private AuthenticatedActor staffActor(UUID tenantId, StaffCredential credential, BranchScope scope,
                                          UUID sessionId) {
        boolean mustChange = credential.isMustChangePassword();
        Set<String> permissions = mustChange
                ? Set.of()
                : roleRepository.findEffectivePermissionCodes(tenantId, credential.getStaffId());
        return new AuthenticatedActor(ActorType.STAFF, credential.getStaffId(), tenantId, credential.getUsername(),
                sessionId, permissions, scope, mustChange, false);
    }

    // ---------------------------------------------------------------------------------------------- Platform

    private AuthOutcome attemptPlatformLogin(String username, String password) {
        Instant now = clock.instant();
        Optional<PlatformUser> found = platformUserRepository.lockByUsername(username);
        if (found.isEmpty()) {
            passwordService.burnDummyCheck(password);
            auditService.record(loginFailure(ActorType.PLATFORM_ADMIN, null, username, "UNKNOWN_USER").build());
            return new AuthOutcome.Failure(IamErrorCode.INVALID_CREDENTIALS);
        }
        PlatformUser user = found.get();
        AuthOutcome.Failure failure = verifyPassword(user, password, now, ActorType.PLATFORM_ADMIN, user.getId());
        if (failure != null) {
            platformUserRepository.save(user);
            return failure;
        }
        if (!user.isActive()) {
            auditService.record(loginFailure(ActorType.PLATFORM_ADMIN, user.getId(), username, "ACCOUNT_DISABLED")
                    .build());
            return new AuthOutcome.Failure(IamErrorCode.ACCOUNT_DISABLED);
        }
        if (user.isMfaEnabled()) {
            return mfaChallenge(PrincipalType.PLATFORM, user.getId(), null, username);
        }
        return completePlatformLogin(user, now, false);
    }

    private AuthOutcome completePlatformLogin(PlatformUser user, Instant now, boolean viaMfa) {
        user.registerSuccessfulLogin(now);
        platformUserRepository.save(user);
        NewSession session = sessionService.open(PrincipalType.PLATFORM, user.getId(), null);
        AuthenticatedActor actor = platformActor(user, session.sessionId());
        auditService.record(AuditEvent.builder("LOGIN_SUCCEEDED", AUTH_RESOURCE)
                .actor(ActorType.PLATFORM_ADMIN, user.getId(), user.getUsername())
                .resourceId(session.sessionId())
                .metadata("mfa", viaMfa)
                .build());
        return new AuthOutcome.Success(tokens(actor, PrincipalType.PLATFORM, session.refreshToken(),
                session.refreshTokenExpiresAt()));
    }

    private Optional<AuthenticatedActor> platformActorForSession(AuthSession session) {
        return platformUserRepository.findById(session.getPrincipalId())
                .filter(PlatformUser::isActive)
                .map(user -> platformActor(user, session.getId()));
    }

    /**
     * Platform administrators without an authenticator get no permissions while the platform requires MFA.
     */
    private AuthenticatedActor platformActor(PlatformUser user, UUID sessionId) {
        boolean mustChange = user.isMustChangePassword();
        boolean mustEnrol = properties.mfa().platformRequired() && !user.isMfaEnabled();
        Set<String> permissions = mustChange || mustEnrol ? Set.of() : user.getPlatformRole().permissions();
        return new AuthenticatedActor(ActorType.PLATFORM_ADMIN, user.getId(), null, user.getUsername(), sessionId,
                permissions, BranchScope.none(), mustChange, mustEnrol);
    }

    // ------------------------------------------------------------------------------------------- Shared flows

    private AuthOutcome mfaChallenge(PrincipalType principalType, UUID principalId, UUID tenantId,
                                     String username) {
        MfaChallengeService.Issued challenge = mfaChallengeService.issue(principalType, principalId, tenantId);
        auditService.record(AuditEvent.builder("LOGIN_MFA_CHALLENGED", AUTH_RESOURCE)
                .actor(principalType.actorType(), principalId, username)
                .resourceId(principalId)
                .build());
        return new AuthOutcome.Success(TokenResponse.mfaChallenge(challenge.token(), challenge.expiresAt()));
    }

    private AuthOutcome attemptMfa(MfaChallengeService.Challenge challenge, String code) {
        Instant now = clock.instant();
        boolean staff = challenge.principalType() == PrincipalType.STAFF;
        LoginCredential credential = staff
                ? staffCredentialRepository.lockByStaffId(challenge.tenantId(), challenge.principalId()).orElse(null)
                : platformUserRepository.lockById(challenge.principalId()).orElse(null);
        if (credential == null || !credential.isMfaEnabled()) {
            return new AuthOutcome.Failure(IamErrorCode.INVALID_MFA_CHALLENGE);
        }
        ActorType actorType = challenge.principalType().actorType();
        if (credential.isLocked(now)) {
            auditService.record(loginFailure(actorType, challenge.principalId(), credential.getUsername(),
                    "ACCOUNT_LOCKED").build());
            return new AuthOutcome.Failure(IamErrorCode.ACCOUNT_LOCKED);
        }
        long step = Totp.verify(mfaSecret(credential, challenge.tenantId(), challenge.principalId(), actorType),
                code, now, credential.getMfaLastUsedStep());
        if (step < 0) {
            boolean lockedNow = credential.registerFailedAttempt(properties.lockout().maxFailedAttempts(),
                    properties.lockout().duration(), now);
            save(credential);
            auditService.record(loginFailure(actorType, challenge.principalId(), credential.getUsername(),
                    "BAD_MFA_CODE").build());
            return new AuthOutcome.Failure(lockedNow ? IamErrorCode.ACCOUNT_LOCKED : IamErrorCode.INVALID_MFA_CODE);
        }
        credential.recordMfaUse(step);
        if (staff) {
            StaffCredential staffCredential = (StaffCredential) credential;
            TenantSummary tenant = tenantService.findById(challenge.tenantId()).orElse(null);
            Optional<StaffAuthProfile> profile = tenant == null ? Optional.empty()
                    : activeStaff(tenant, staffCredential);
            if (profile.isEmpty()) {
                save(credential);
                return new AuthOutcome.Failure(IamErrorCode.ACCOUNT_DISABLED);
            }
            return completeStaffLogin(challenge.tenantId(), staffCredential, profile.get(), now, true);
        }
        PlatformUser user = (PlatformUser) credential;
        if (!user.isActive()) {
            save(credential);
            return new AuthOutcome.Failure(IamErrorCode.ACCOUNT_DISABLED);
        }
        return completePlatformLogin(user, now, true);
    }

    private AuthOutcome attemptRefresh(String refreshToken, ParsedRefreshToken parsed) {
        RotationResult result = sessionService.rotate(refreshToken);
        if (result instanceof RotationResult.Rejected rejected) {
            if (rejected.reuseDetected()) {
                auditService.record(AuditEvent.builder("REFRESH_TOKEN_REUSE_DETECTED", "AUTH_SESSION")
                        .resourceId(rejected.sessionId())
                        .outcome(AuditOutcome.DENIED)
                        .build());
            }
            return new AuthOutcome.Failure(IamErrorCode.INVALID_REFRESH_TOKEN);
        }
        RotationResult.Rotated rotated = (RotationResult.Rotated) result;
        AuthSession session = rotated.session();
        if (session.getPrincipalType() != parsed.principalType()) {
            sessionService.revoke(session.getId(), SessionService.REASON_ACCOUNT_DISABLED);
            return new AuthOutcome.Failure(IamErrorCode.INVALID_REFRESH_TOKEN);
        }
        Optional<AuthenticatedActor> actor = session.getPrincipalType() == PrincipalType.STAFF
                ? staffActorForSession(session)
                : platformActorForSession(session);
        if (actor.isEmpty()) {
            sessionService.revoke(session.getId(), SessionService.REASON_ACCOUNT_DISABLED);
            return new AuthOutcome.Failure(IamErrorCode.INVALID_REFRESH_TOKEN);
        }
        return new AuthOutcome.Success(tokens(actor.get(), session.getPrincipalType(), rotated.refreshToken(),
                rotated.refreshTokenExpiresAt()));
    }

    private AuthOutcome attemptPasswordChange(AuthenticatedActor actor, ChangePasswordRequest request) {
        Instant now = clock.instant();
        PrincipalType principalType = principalTypeOf(actor);
        LoginCredential credential = lockCredentialOf(actor);
        AuthOutcome.Failure failure = verifyPassword(credential, request.currentPassword(), now, actor.type(),
                actor.id());
        if (failure != null) {
            save(credential);
            return failure;
        }
        passwordService.validateNewPassword(request.newPassword(), credential.getUsername(),
                credential.getPasswordHash());
        credential.changePassword(passwordService.hash(request.newPassword()), false, now);
        save(credential);

        sessionService.revokeAllOfPrincipal(principalType, actor.id(), SessionService.REASON_PASSWORD_CHANGED);
        NewSession session = sessionService.open(principalType, actor.id(), actor.tenantId());
        AuthenticatedActor refreshed = principalType == PrincipalType.STAFF
                ? staffActor(actor.tenantId(), staffCredentialRepository.findByTenantIdAndStaffId(actor.tenantId(),
                        actor.id()).orElseThrow(), actor.branchScope(), session.sessionId())
                : platformActor(platformUserRepository.findById(actor.id()).orElseThrow(), session.sessionId());
        auditService.record(AuditEvent.builder("PASSWORD_CHANGED", AUTH_RESOURCE).resourceId(actor.id()).build());
        return new AuthOutcome.Success(tokens(refreshed, principalType, session.refreshToken(),
                session.refreshTokenExpiresAt()));
    }

    /**
     * @return {@code null} when the password is correct and the credential is not locked
     */
    private AuthOutcome.Failure verifyPassword(LoginCredential credential, String password, Instant now,
                                               ActorType actorType, UUID actorId) {
        if (credential.isLocked(now)) {
            passwordService.burnDummyCheck(password);
            auditService.record(loginFailure(actorType, actorId, credential.getUsername(), "ACCOUNT_LOCKED")
                    .build());
            return new AuthOutcome.Failure(IamErrorCode.ACCOUNT_LOCKED);
        }
        if (passwordService.matches(password, credential.getPasswordHash())) {
            return null;
        }
        boolean lockedNow = credential.registerFailedAttempt(properties.lockout().maxFailedAttempts(),
                properties.lockout().duration(), now);
        auditService.record(loginFailure(actorType, actorId, credential.getUsername(), "BAD_PASSWORD").build());
        if (lockedNow) {
            auditService.record(AuditEvent.builder("ACCOUNT_LOCKED", AUTH_RESOURCE)
                    .actor(actorType, actorId, credential.getUsername())
                    .resourceId(actorId)
                    .outcome(AuditOutcome.DENIED)
                    .metadata("lockedUntil", credential.getLockedUntil())
                    .build());
            return new AuthOutcome.Failure(IamErrorCode.ACCOUNT_LOCKED);
        }
        return new AuthOutcome.Failure(IamErrorCode.INVALID_CREDENTIALS);
    }

    private LoginCredential lockCredentialOf(AuthenticatedActor actor) {
        LoginCredential credential = principalTypeOf(actor) == PrincipalType.STAFF
                ? staffCredentialRepository.lockByStaffId(actor.tenantId(), actor.id()).orElse(null)
                : platformUserRepository.lockById(actor.id()).orElse(null);
        if (credential == null) {
            throw new BankingException(CommonErrorCode.UNAUTHENTICATED);
        }
        return credential;
    }

    private byte[] mfaSecret(LoginCredential credential, UUID tenantId, UUID principalId, ActorType actorType) {
        String context = mfaContext(tenantId, principalId, actorType);
        return Base32.decode(encryption.decrypt(credential.getMfaSecretEncrypted(), context));
    }

    private static String mfaContext(AuthenticatedActor actor) {
        return mfaContext(actor.tenantId(), actor.id(), actor.type());
    }

    private static String mfaContext(UUID tenantId, UUID principalId, ActorType actorType) {
        String table = actorType == ActorType.PLATFORM_ADMIN ? "platform_user" : "staff_credential";
        return (tenantId == null ? "platform" : tenantId.toString()) + "/" + table + "/" + principalId + "/mfa_secret";
    }

    private static PrincipalType principalTypeOf(AuthenticatedActor actor) {
        return actor.type() == ActorType.PLATFORM_ADMIN ? PrincipalType.PLATFORM : PrincipalType.STAFF;
    }

    private void save(LoginCredential credential) {
        if (credential instanceof StaffCredential staffCredential) {
            staffCredentialRepository.save(staffCredential);
        } else if (credential instanceof PlatformUser platformUser) {
            platformUserRepository.save(platformUser);
        }
    }

    private TokenResponse tokens(AuthenticatedActor actor, PrincipalType principalType, String refreshToken,
                                 Instant refreshTokenExpiresAt) {
        AccessTokenService.IssuedAccessToken access = accessTokenService.issue(actor, principalType);
        return TokenResponse.bearer(access.value(), access.expiresAt(), refreshToken, refreshTokenExpiresAt,
                actor.passwordChangeRequired(), actor.mfaEnrollmentRequired());
    }

    private static AuditEvent.Builder loginFailure(ActorType actorType, UUID actorId, String username,
                                                   String reason) {
        return AuditEvent.builder("LOGIN_FAILED", AUTH_RESOURCE)
                .actor(actorId == null ? ActorType.ANONYMOUS : actorType, actorId, username)
                .outcome(AuditOutcome.FAILURE)
                .metadata("reason", reason)
                .metadata("username", username);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private sealed interface AuthOutcome {

        default TokenResponse tokensOrThrow() {
            if (this instanceof Success success) {
                return success.tokens();
            }
            throw new BankingException(((Failure) this).code());
        }

        record Success(TokenResponse tokens) implements AuthOutcome {
        }

        record Failure(ErrorCode code) implements AuthOutcome {
        }
    }
}
