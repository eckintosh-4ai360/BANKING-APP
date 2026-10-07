package com.company.banking.iam.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.iam.dto.PlatformMeResponse;
import com.company.banking.iam.entity.PlatformRole;
import com.company.banking.iam.entity.PlatformUser;
import com.company.banking.iam.repository.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Platform administrators. Operates only in platform context (no tenant bound).
 */
@Service
@RequiredArgsConstructor
public class PlatformUserService {

    private final PlatformUserRepository platformUserRepository;
    private final PasswordService passwordService;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Creates the first platform owner when none exists. Idempotent.
     *
     * @return {@code true} if a user was created
     */
    @Transactional
    public boolean bootstrapOwnerIfAbsent(String username, String email, String fullName, String rawPassword,
                                          boolean mustChangePassword) {
        if (platformUserRepository.count() > 0) {
            return false;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        passwordService.validateNewPassword(rawPassword, normalized, null);
        PlatformUser owner = platformUserRepository.saveAndFlush(new PlatformUser(UuidV7.next(), normalized, email,
                fullName, PlatformRole.PLATFORM_OWNER, passwordService.hash(rawPassword), mustChangePassword,
                clock.instant()));
        auditService.record(AuditEvent.builder("PLATFORM_USER_BOOTSTRAPPED", "PLATFORM_USER")
                .resourceId(owner.getId())
                .resourceReference(owner.getUsername())
                .after(Map.of("username", owner.getUsername(), "role", owner.getPlatformRole()))
                .build());
        return true;
    }

    @Transactional(readOnly = true)
    public PlatformMeResponse me() {
        AuthenticatedActor actor = CurrentActor.require();
        PlatformUser user = platformUserRepository.findById(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Platform user"));
        return new PlatformMeResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getPlatformRole().name(), new TreeSet<>(actor.permissions()).stream().toList(),
                actor.passwordChangeRequired());
    }
}
