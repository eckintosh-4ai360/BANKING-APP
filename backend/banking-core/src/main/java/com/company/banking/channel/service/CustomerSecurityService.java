package com.company.banking.channel.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.channel.dto.ChannelDtos;
import com.company.banking.channel.entity.CustomerCredential;
import com.company.banking.channel.entity.CustomerDevice;
import com.company.banking.channel.entity.OtpChallenge;
import com.company.banking.channel.exception.ChannelErrorCode;
import com.company.banking.channel.repository.CustomerCredentialRepository;
import com.company.banking.channel.repository.CustomerDeviceRepository;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.web.RequestMetadata;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.service.CustomerService;
import com.company.banking.iam.dto.SessionInfo;
import com.company.banking.iam.exception.IamErrorCode;
import com.company.banking.iam.security.BankingSecurityProperties;
import com.company.banking.iam.service.CustomerSessionService;
import com.company.banking.iam.service.PasswordService;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.service.TenantService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The signed-in customer's own security: profile, trusted devices, sign-in history and sessions, and the
 * transaction PIN (change, or reset with a texted code and the password).
 */
@Service
@RequiredArgsConstructor
public class CustomerSecurityService {

    private static final int HISTORY = 30;
    private static final String REASON_DEVICE_REVOKED = "DEVICE_REVOKED";
    private static final String REASON_ENDED_BY_CUSTOMER = "ENDED_BY_CUSTOMER";

    private final CustomerCredentialRepository credentials;
    private final CustomerDeviceRepository devices;
    private final CustomerService customerService;
    private final CustomerSessionService customerSessions;
    private final OtpService otp;
    private final PinService pinService;
    private final PasswordService passwordService;
    private final TenantService tenantService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final BankingSecurityProperties securityProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ChannelDtos.Profile profile() {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        CustomerCredential credential = credential(actor);
        CustomerSummary customer = customerService.findForChannel(actor.id())
                .orElseThrow(() -> new BankingException(CommonErrorCode.UNAUTHENTICATED));
        return new ChannelDtos.Profile(customer.id(), customer.customerNumber(), customer.displayName(),
                credential.getUsername(), customer.status(), customer.kycStatus(), customer.kycTierCode(),
                credential.getLastLoginAt(), credential.isPinLocked());
    }

    @Transactional(readOnly = true)
    public List<ChannelDtos.Device> devices() {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        String deviceKey = RequestMetadata.current().deviceId();
        return devices.findAllByTenantIdAndCustomerIdOrderByBoundAtDesc(actor.tenantId(), actor.id()).stream()
                .map(device -> new ChannelDtos.Device(device.getId(), device.getName(), device.getPlatform(),
                        device.getStatus().name(), device.getBoundAt(), device.getLastSeenAt(), device.getRevokedAt(),
                        device.isActive() && device.getDeviceKey().equals(deviceKey)))
                .toList();
    }

    /**
     * Stops trusting a device and ends its sessions (signing in there needs a texted code again).
     */
    @Transactional
    public void revokeDevice(UUID deviceId) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        CustomerDevice device = devices.findByTenantIdAndId(actor.tenantId(), deviceId)
                .filter(found -> found.getCustomerId().equals(actor.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Device"));
        if (!device.isActive()) {
            return;
        }
        device.revoke(REASON_DEVICE_REVOKED, clock.instant());
        devices.save(device);
        customerSessions.revokeDevice(actor.id(), deviceId, REASON_DEVICE_REVOKED);
        auditService.record(AuditEvent.builder("CUSTOMER_DEVICE_REVOKED", PinService.RESOURCE)
                .resourceId(deviceId)
                .metadata("deviceName", device.getName())
                .build());
    }

    @Transactional(readOnly = true)
    public List<ChannelDtos.Session> sessions() {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        Map<UUID, String> names = devices.findAllByTenantIdAndCustomerIdOrderByBoundAtDesc(actor.tenantId(),
                        actor.id()).stream()
                .collect(Collectors.toMap(CustomerDevice::getId, CustomerDevice::getName, (first, second) -> first));
        return customerSessions.recent(actor.id(), HISTORY).stream()
                .map(session -> toResponse(session, names))
                .toList();
    }

    @Transactional
    public void endSession(UUID sessionId) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        if (!customerSessions.revoke(actor.id(), sessionId, REASON_ENDED_BY_CUSTOMER)) {
            throw new ResourceNotFoundException("Session");
        }
        auditService.record(AuditEvent.builder("CUSTOMER_SESSION_ENDED", "AUTH_SESSION")
                .resourceId(sessionId)
                .build());
    }

    // ------------------------------------------------------------------------------------------------ PIN

    public void changePin(ChannelDtos.PinChange request) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        PinService.requireAcceptable(request.newPin());
        String institution = institutionName(actor);
        transactionTemplate.execute(status -> {
            CustomerCredential credential = lockCredential(actor);
            PinService.Check check = pinService.check(credential, request.currentPin());
            if (check != PinService.Check.RIGHT) {
                credentials.save(credential);
                return CustomerAuthService.Outcome.failure(check == PinService.Check.LOCKED
                        ? new BankingException(ChannelErrorCode.PIN_LOCKED) : pinService.wrongPin(credential));
            }
            credential.changePin(pinService.hash(request.newPin()), clock.instant());
            credentials.save(credential);
            auditService.record(AuditEvent.builder("PIN_CHANGED", PinService.RESOURCE).resourceId(actor.id()).build());
            otp.noticeAfterCommit(credential.getUsername(), institution + ": your transaction PIN was changed."
                    + " If this was not you, contact us at once.");
            return CustomerAuthService.Outcome.success(null);
        }).throwIfFailed();
    }

    /**
     * Texts a code to reset a forgotten or locked PIN.
     */
    @Transactional
    public ChannelDtos.CodeSent startPinReset() {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        String deviceKey = CustomerAuthService.requireDeviceKey();
        CustomerCredential credential = credential(actor);
        OtpService.Issued issued = otp.send(actor.tenantId(), actor.id(), credential.getUsername(),
                OtpChallenge.Purpose.PIN_RESET, deviceKey, institutionName(actor));
        return new ChannelDtos.CodeSent(issued.token(), issued.maskedPhone(), issued.expiresAt());
    }

    /**
     * Sets a new PIN with the texted code and the password, and unlocks it.
     */
    public void completePinReset(ChannelDtos.PinResetCompletion request) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        String deviceKey = CustomerAuthService.requireDeviceKey();
        PinService.requireAcceptable(request.newPin());
        ChallengeTokens.Parsed parsed = ChallengeTokens.parse(request.challengeToken())
                .filter(token -> token.tenantId().equals(actor.tenantId()))
                .orElseThrow(() -> new BankingException(ChannelErrorCode.CODE_INVALID));
        String institution = institutionName(actor);
        transactionTemplate.execute(status -> {
            Optional<OtpChallenge> challenge = otp.verify(actor.tenantId(), parsed.challengeId(), request.code(),
                    OtpChallenge.Purpose.PIN_RESET, deviceKey)
                    .filter(found -> found.getCustomerId().equals(actor.id()));
            if (challenge.isEmpty()) {
                return CustomerAuthService.Outcome.failure(ChannelErrorCode.CODE_INVALID);
            }
            Instant now = clock.instant();
            CustomerCredential credential = lockCredential(actor);
            if (credential.isLocked(now) || !passwordService.matches(request.password(), credential.passwordHash())) {
                boolean lockedNow = !credential.isLocked(now) && credential.registerFailedLogin(
                        securityProperties.lockout().maxFailedAttempts(), securityProperties.lockout().duration(), now);
                credentials.save(credential);
                return CustomerAuthService.Outcome.failure(lockedNow || credential.isLocked(now)
                        ? IamErrorCode.ACCOUNT_LOCKED : IamErrorCode.INVALID_CREDENTIALS);
            }
            credential.changePin(pinService.hash(request.newPin()), now);
            credentials.save(credential);
            auditService.record(AuditEvent.builder("PIN_RESET", PinService.RESOURCE).resourceId(actor.id()).build());
            otp.noticeAfterCommit(credential.getUsername(), institution + ": your transaction PIN was reset."
                    + " If this was not you, contact us at once.");
            return CustomerAuthService.Outcome.success(null);
        }).throwIfFailed();
    }

    // -------------------------------------------------------------------------------------------- helpers

    private CustomerCredential credential(AuthenticatedActor actor) {
        return credentials.findByTenantIdAndCustomerId(actor.tenantId(), actor.id())
                .orElseThrow(() -> new BankingException(CommonErrorCode.UNAUTHENTICATED));
    }

    private CustomerCredential lockCredential(AuthenticatedActor actor) {
        return credentials.lockByCustomerId(actor.tenantId(), actor.id())
                .orElseThrow(() -> new BankingException(CommonErrorCode.UNAUTHENTICATED));
    }

    private String institutionName(AuthenticatedActor actor) {
        return tenantService.findById(actor.tenantId()).map(TenantSummary::displayName).orElse("Your bank");
    }

    private static ChannelDtos.Session toResponse(SessionInfo session, Map<UUID, String> deviceNames) {
        return new ChannelDtos.Session(session.id(), session.deviceId(), deviceNames.get(session.deviceId()),
                session.status(), session.createdAt(), session.lastRefreshedAt(), session.revokedAt(),
                session.revokeReason(), session.ipAddress(), session.current());
    }
}
