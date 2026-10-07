package com.company.banking.iam.service;

import com.company.banking.common.error.DuplicateResourceException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.iam.dto.CredentialInfo;
import com.company.banking.iam.dto.IssuedCredential;
import com.company.banking.iam.entity.PrincipalType;
import com.company.banking.iam.entity.StaffCredential;
import com.company.banking.iam.repository.StaffCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Staff login credentials, used by the staff module. Callers are responsible for authorization and audit of the
 * business action; this service never returns password hashes.
 */
@Service
@RequiredArgsConstructor
public class StaffCredentialService {

    private final StaffCredentialRepository credentialRepository;
    private final PasswordService passwordService;
    private final SessionService sessionService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isUsernameTaken(String username) {
        return credentialRepository.existsByTenantIdAndUsername(TenantContext.requireTenantId(),
                normalize(username));
    }

    /**
     * Creates a credential with a one-time temporary password that must be changed at first login.
     */
    @Transactional
    public IssuedCredential createWithTemporaryPassword(UUID staffId, String username) {
        String normalized = normalize(username);
        if (isUsernameTaken(normalized)) {
            throw new DuplicateResourceException("The username " + normalized + " is already in use.");
        }
        String temporaryPassword = passwordService.generateTemporaryPassword();
        credentialRepository.saveAndFlush(new StaffCredential(staffId, TenantContext.requireTenantId(), normalized,
                passwordService.hash(temporaryPassword), true, clock.instant()));
        return new IssuedCredential(normalized, temporaryPassword);
    }

    /**
     * Issues a new temporary password, unlocks the login and ends all sessions.
     */
    @Transactional
    public IssuedCredential resetWithTemporaryPassword(UUID staffId) {
        StaffCredential credential = lock(staffId);
        String temporaryPassword = passwordService.generateTemporaryPassword();
        credential.changePassword(passwordService.hash(temporaryPassword), true, clock.instant());
        credential.resetMfa();
        credentialRepository.saveAndFlush(credential);
        sessionService.revokeAllOfPrincipal(PrincipalType.STAFF, staffId, SessionService.REASON_CREDENTIAL_RESET);
        return new IssuedCredential(credential.getUsername(), temporaryPassword);
    }

    /**
     * Sets a known password. Only for local demo seeding; the password must satisfy the policy.
     */
    @Transactional
    public void setPasswordForSeeding(UUID staffId, String rawPassword) {
        StaffCredential credential = lock(staffId);
        passwordService.validateNewPassword(rawPassword, credential.getUsername(), null);
        credential.changePassword(passwordService.hash(rawPassword), false, clock.instant());
        credentialRepository.saveAndFlush(credential);
    }

    /**
     * Disabling also ends all sessions immediately.
     */
    @Transactional
    public void setLoginEnabled(UUID staffId, boolean enabled, String reason) {
        StaffCredential credential = lock(staffId);
        credential.setLoginEnabled(enabled);
        credentialRepository.saveAndFlush(credential);
        if (!enabled) {
            sessionService.revokeAllOfPrincipal(PrincipalType.STAFF, staffId, reason);
        }
    }

    /**
     * Ends all sessions so a changed scope or profile is reflected in new tokens.
     */
    @Transactional
    public void revokeSessions(UUID staffId, String reason) {
        sessionService.revokeAllOfPrincipal(PrincipalType.STAFF, staffId, reason);
    }

    @Transactional(readOnly = true)
    public Optional<CredentialInfo> credentialInfo(UUID staffId) {
        Instant now = clock.instant();
        return credentialRepository.findByTenantIdAndStaffId(TenantContext.requireTenantId(), staffId)
                .map(credential -> new CredentialInfo(credential.getUsername(), credential.isLoginEnabled(),
                        credential.isMustChangePassword(), credential.isLocked(now), credential.getLockedUntil(),
                        credential.getLastLoginAt(), credential.getPasswordChangedAt()));
    }

    private StaffCredential lock(UUID staffId) {
        return credentialRepository.lockByStaffId(TenantContext.requireTenantId(), staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff credential"));
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
