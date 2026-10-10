package com.company.banking.channel.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditOutcome;
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
import com.company.banking.common.error.ErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.ActorType;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.common.text.PhoneNumbers;
import com.company.banking.common.web.RequestMetadata;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.service.CustomerService;
import com.company.banking.iam.dto.TokenResponse;
import com.company.banking.iam.exception.IamErrorCode;
import com.company.banking.iam.security.BankingSecurityProperties;
import com.company.banking.iam.service.CustomerSessionService;
import com.company.banking.iam.service.LoginRateLimiter;
import com.company.banking.iam.service.PasswordService;
import com.company.banking.iam.service.SessionService;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.service.InstitutionService;
import com.company.banking.tenant.service.TenantService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Customer sign-in for the mobile app.
 *
 * <ul>
 *   <li><b>Activation</b>: a customer onboarded at a branch sets up mobile banking with their customer number and the
 *   phone number on file; a code texted to that phone proves it is theirs, then they choose a password and a
 *   transaction PIN, and the installation becomes their first trusted device.</li>
 *   <li><b>Sign-in</b>: phone number and password. On a trusted device that gives tokens; on any other device a
 *   code is texted first, and answering it trusts the device (the customer is told by text).</li>
 *   <li><b>Password reset</b> needs a texted code and the transaction PIN, so a stolen SIM alone is not enough.</li>
 * </ul>
 * Requests that match no customer get the same answers as those that do (no code is sent), so the API does not
 * reveal who banks here. Failed attempts lock the sign-in, and every failure is audited.
 */
@Service
@RequiredArgsConstructor
public class CustomerAuthService {

    static final String MOBILE_FEATURE = "CUSTOMER_MOBILE_APP";
    /** PENDING: signed up in the app and still finishing it (they hold no accounts until approved). */
    static final Set<String> SIGN_IN_STATUSES = Set.of("ACTIVE", "DORMANT", "RESTRICTED", "PENDING");
    private static final String AUTH_RESOURCE = "AUTHENTICATION";
    private static final int DEVICE_NAME_MAX = 40;

    private final TenantService tenantService;
    private final InstitutionService institutionService;
    private final CustomerService customerService;
    private final CustomerCredentialRepository credentials;
    private final CustomerDeviceRepository devices;
    private final OtpService otp;
    private final CustomerInbox inbox;
    private final CustomerOnboardingService onboarding;
    private final PinService pinService;
    private final PasswordService passwordService;
    private final LoginRateLimiter rateLimiter;
    private final CustomerSessionService customerSessions;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final BankingSecurityProperties securityProperties;
    private final Clock clock;

    // -------------------------------------------------------------------------------------------- sign-in

    public TokenResponse login(ChannelDtos.Login request) {
        String deviceKey = requireDeviceKey();
        String institutionCode = normalizeCode(request.institutionCode());
        rateLimiter.check("customer:" + institutionCode + ":" + fingerprint(request.phoneNumber()));
        Optional<TenantSummary> tenant = tenantService.findByCode(institutionCode).filter(TenantSummary::isActive);
        Optional<String> phone = tenant.flatMap(found -> PhoneNumbers.normalize(request.phoneNumber(),
                found.countryCode()));
        if (tenant.isEmpty() || phone.isEmpty()) {
            passwordService.burnDummyCheck(request.password());
            throw new BankingException(IamErrorCode.INVALID_CREDENTIALS);
        }
        return inTenant(tenant.get(), () -> attemptLogin(tenant.get(), phone.get(), request, deviceKey))
                .tokensOrThrow();
    }

    private Outcome attemptLogin(TenantSummary tenant, String phone, ChannelDtos.Login request, String deviceKey) {
        Instant now = clock.instant();
        if (!institutionService.isFeatureEnabled(MOBILE_FEATURE)) {
            passwordService.burnDummyCheck(request.password());
            return Outcome.failure(ChannelErrorCode.CHANNEL_NOT_ENABLED);
        }
        Optional<CustomerCredential> found = credentials.lockByUsername(tenant.id(), phone);
        if (found.isEmpty()) {
            passwordService.burnDummyCheck(request.password());
            auditService.record(loginFailure(null, "UNKNOWN_USER").build());
            return Outcome.failure(IamErrorCode.INVALID_CREDENTIALS);
        }
        CustomerCredential credential = found.get();
        Outcome refused = checkPassword(credential, request.password(), tenant, now);
        if (refused != null) {
            return refused;
        }
        Optional<CustomerSummary> customer = signInAllowed(credential);
        if (customer.isEmpty()) {
            credentials.save(credential);
            return Outcome.failure(IamErrorCode.ACCOUNT_DISABLED);
        }
        Optional<CustomerDevice> trusted = devices.findTrusted(tenant.id(), credential.getCustomerId(), deviceKey);
        if (trusted.isPresent()) {
            trusted.get().seen(now);
            devices.save(trusted.get());
            return Outcome.success(signIn(tenant, credential, customer.get(), trusted.get(), now));
        }
        credentials.save(credential);
        OtpService.Issued issued = otp.send(tenant.id(), credential.getCustomerId(), phone,
                OtpChallenge.Purpose.DEVICE_BINDING, deviceKey, tenant.displayName());
        auditService.record(AuditEvent.builder("LOGIN_DEVICE_CHALLENGED", AUTH_RESOURCE)
                .actor(ActorType.CUSTOMER, credential.getCustomerId(), customer.get().customerNumber())
                .resourceId(credential.getCustomerId())
                .build());
        return Outcome.success(TokenResponse.mfaChallenge(issued.token(), issued.expiresAt()));
    }

    /**
     * Trusts the installation with the code texted at sign-in and signs the customer in on it.
     */
    public TokenResponse verifyDevice(ChannelDtos.DeviceVerification request) {
        String deviceKey = requireDeviceKey();
        ChallengeTokens.Parsed parsed = parseChallenge(request.challengeToken());
        TenantSummary tenant = challengeTenant(parsed);
        return inTenant(tenant, () -> {
            if (!institutionService.isFeatureEnabled(MOBILE_FEATURE)) {
                return Outcome.failure(ChannelErrorCode.CHANNEL_NOT_ENABLED);
            }
            Optional<OtpChallenge> challenge = otp.verify(tenant.id(), parsed.challengeId(), request.code(),
                    OtpChallenge.Purpose.DEVICE_BINDING, deviceKey);
            if (challenge.isEmpty()) {
                return Outcome.failure(ChannelErrorCode.CODE_INVALID);
            }
            CustomerCredential credential = credentials.lockByCustomerId(tenant.id(),
                    challenge.get().getCustomerId()).orElseThrow();
            Optional<CustomerSummary> customer = signInAllowed(credential);
            if (customer.isEmpty()) {
                return Outcome.failure(IamErrorCode.ACCOUNT_DISABLED);
            }
            Instant now = clock.instant();
            CustomerDevice device = devices.findTrusted(tenant.id(), credential.getCustomerId(), deviceKey)
                    .orElseGet(() -> devices.save(new CustomerDevice(UuidV7.next(), tenant.id(),
                            credential.getCustomerId(), deviceKey, deviceName(request.deviceName()),
                            platform(request.platform()), now)));
            auditService.record(AuditEvent.builder("CUSTOMER_DEVICE_TRUSTED", AUTH_RESOURCE)
                    .actor(ActorType.CUSTOMER, credential.getCustomerId(), customer.get().customerNumber())
                    .resourceId(device.getId())
                    .metadata("deviceName", device.getName())
                    .build());
            inbox.securityNotice(credential.getCustomerId(), credential.getUsername(), tenant.displayName(),
                    "New device", "a new device (" + device.getName() + ") was added to your mobile banking. If this"
                            + " was not you, contact us at once.");
            return Outcome.success(signIn(tenant, credential, customer.get(), device, now));
        }).tokensOrThrow();
    }

    // ----------------------------------------------------------------------------------------- activation

    /**
     * Texts a code to the phone on file when the customer number and phone number match an active customer who has
     * not set up mobile banking yet. The answer looks the same either way.
     */
    public ChannelDtos.CodeSent startActivation(ChannelDtos.Activation request) {
        String deviceKey = requireDeviceKey();
        TenantSummary tenant = institution(request.institutionCode());
        String phone = PhoneNumbers.normalize(request.phoneNumber(), tenant.countryCode())
                .orElseThrow(() -> new BankingException(ChannelErrorCode.INVALID_PHONE_NUMBER));
        rateLimiter.check("customer-activation:" + fingerprint(phone));
        return TenantContext.callAs(tenant.id(), () -> transactionTemplate.execute(status -> {
            requireChannelEnabled();
            Optional<CustomerSummary> customer = customerService.findByNumberForChannel(request.customerNumber())
                    .filter(found -> "ACTIVE".equals(found.status()))
                    .filter(found -> PhoneNumbers.normalize(found.primaryPhone(), tenant.countryCode())
                            .map(phone::equals).orElse(false));
            boolean eligible = customer.isPresent()
                    && !credentials.existsByTenantIdAndCustomerId(tenant.id(), customer.get().id())
                    && !credentials.existsByTenantIdAndUsername(tenant.id(), phone);
            OtpService.Issued issued;
            if (eligible) {
                issued = otp.send(tenant.id(), customer.get().id(), phone, OtpChallenge.Purpose.ACTIVATION,
                        deviceKey, tenant.displayName());
            } else {
                if (customer.isPresent()) {
                    otp.noticeAfterCommit(phone, tenant.displayName() + ": mobile banking is already set up for"
                            + " this number. Sign in with your password, or choose Forgot password.");
                }
                issued = otp.decoy(tenant.id(), phone, OtpChallenge.Purpose.ACTIVATION, deviceKey);
            }
            auditService.record(AuditEvent.builder("MOBILE_BANKING_ACTIVATION_REQUESTED", AUTH_RESOURCE)
                    .actor(ActorType.ANONYMOUS, null, null)
                    .resourceId(issued.challengeId())
                    .metadata("matched", eligible)
                    .build());
            return new ChannelDtos.CodeSent(issued.token(), issued.maskedPhone(), issued.expiresAt());
        }));
    }

    /**
     * Sets up the credential with the texted code: password, transaction PIN and this installation as the first
     * trusted device; signs the customer in.
     */
    public TokenResponse completeActivation(ChannelDtos.ActivationCompletion request) {
        String deviceKey = requireDeviceKey();
        PinService.requireAcceptable(request.pin());
        ChallengeTokens.Parsed parsed = parseChallenge(request.challengeToken());
        TenantSummary tenant = challengeTenant(parsed);
        return inTenant(tenant, () -> {
            requireChannelEnabled();
            String phone = otp.phoneOf(tenant.id(), parsed.challengeId()).orElse(null);
            passwordService.validateNewPassword(request.password(), phone, null);
            Optional<OtpChallenge> challenge = otp.verify(tenant.id(), parsed.challengeId(), request.code(),
                    OtpChallenge.Purpose.ACTIVATION, deviceKey);
            if (challenge.isEmpty()) {
                return Outcome.failure(ChannelErrorCode.CODE_INVALID);
            }
            UUID customerId = challenge.get().getCustomerId();
            Optional<CustomerSummary> customer = customerService.findForChannel(customerId)
                    .filter(found -> "ACTIVE".equals(found.status()));
            if (customer.isEmpty()) {
                return Outcome.failure(IamErrorCode.ACCOUNT_DISABLED);
            }
            if (credentials.existsByTenantIdAndCustomerId(tenant.id(), customerId)
                    || credentials.existsByTenantIdAndUsername(tenant.id(), challenge.get().getPhone())) {
                return Outcome.failure(ChannelErrorCode.ALREADY_SET_UP);
            }
            Instant now = clock.instant();
            CustomerCredential credential = credentials.saveAndFlush(new CustomerCredential(customerId, tenant.id(),
                    challenge.get().getPhone(), passwordService.hash(request.password()), pinService.hash(request.pin()),
                    now));
            CustomerDevice device = devices.saveAndFlush(new CustomerDevice(UuidV7.next(), tenant.id(), customerId,
                    deviceKey, deviceName(request.deviceName()), platform(request.platform()), now));
            auditService.record(AuditEvent.builder("MOBILE_BANKING_ACTIVATED", PinService.RESOURCE)
                    .actor(ActorType.CUSTOMER, customerId, customer.get().customerNumber())
                    .resourceId(customerId)
                    .metadata("deviceId", device.getId())
                    .build());
            inbox.securityNotice(customerId, credential.getUsername(), tenant.displayName(), "Welcome",
                    "mobile banking is now set up for you. If this was not you, contact us at once.");
            return Outcome.success(signIn(tenant, credential, customer.get(), device, now));
        }).tokensOrThrow();
    }

    // ---------------------------------------------------------------------------------------------- sign-up

    /**
     * Texts a code to a phone number without mobile banking, when the institution offers sign-up in the app. A number
     * that already has mobile banking gets a reminder instead of a code; the answer looks the same either way.
     */
    public ChannelDtos.CodeSent startSignUp(ChannelDtos.SignUp request) {
        String deviceKey = requireDeviceKey();
        TenantSummary tenant = institution(request.institutionCode());
        String phone = PhoneNumbers.normalize(request.phoneNumber(), tenant.countryCode())
                .orElseThrow(() -> new BankingException(ChannelErrorCode.INVALID_PHONE_NUMBER));
        rateLimiter.check("customer-sign-up:" + fingerprint(phone));
        return TenantContext.callAs(tenant.id(), () -> transactionTemplate.execute(status -> {
            requireChannelEnabled();
            onboarding.requireSignUpOpen();
            boolean taken = credentials.existsByTenantIdAndUsername(tenant.id(), phone);
            OtpService.Issued issued;
            if (taken) {
                otp.noticeAfterCommit(phone, tenant.displayName() + ": mobile banking is already set up for this"
                        + " number. Sign in with your password, or choose Forgot password.");
                issued = otp.decoy(tenant.id(), phone, OtpChallenge.Purpose.REGISTRATION, deviceKey);
            } else {
                issued = otp.send(tenant.id(), null, phone, OtpChallenge.Purpose.REGISTRATION, deviceKey,
                        tenant.displayName());
            }
            auditService.record(AuditEvent.builder("MOBILE_BANKING_SIGN_UP_REQUESTED", AUTH_RESOURCE)
                    .actor(ActorType.ANONYMOUS, null, null)
                    .resourceId(issued.challengeId())
                    .metadata("alreadySetUp", taken)
                    .build());
            return new ChannelDtos.CodeSent(issued.token(), issued.maskedPhone(), issued.expiresAt());
        }));
    }

    /**
     * Registers with the texted code: a new customer, pending until their details are approved, with a password, a
     * transaction PIN and this installation as the first trusted device; signs them in to finish signing up.
     */
    public TokenResponse completeSignUp(ChannelDtos.SignUpCompletion request) {
        String deviceKey = requireDeviceKey();
        PinService.requireAcceptable(request.pin());
        ChallengeTokens.Parsed parsed = parseChallenge(request.challengeToken());
        TenantSummary tenant = challengeTenant(parsed);
        return inTenant(tenant, () -> {
            requireChannelEnabled();
            onboarding.requireSignUpOpen();
            onboarding.requireOldEnough(request.dateOfBirth());
            String phone = otp.phoneOf(tenant.id(), parsed.challengeId()).orElse(null);
            passwordService.validateNewPassword(request.password(), phone, null);
            Optional<OtpChallenge> challenge = otp.verify(tenant.id(), parsed.challengeId(), request.code(),
                    OtpChallenge.Purpose.REGISTRATION, deviceKey);
            if (challenge.isEmpty()) {
                return Outcome.failure(ChannelErrorCode.CODE_INVALID);
            }
            if (credentials.existsByTenantIdAndUsername(tenant.id(), challenge.get().getPhone())) {
                return Outcome.failure(ChannelErrorCode.ALREADY_SET_UP);
            }
            Instant now = clock.instant();
            CustomerSummary customer = onboarding.register(challenge.get().getPhone(), request.firstName(),
                    request.lastName(), request.dateOfBirth());
            CustomerCredential credential = credentials.saveAndFlush(new CustomerCredential(customer.id(), tenant.id(),
                    challenge.get().getPhone(), passwordService.hash(request.password()), pinService.hash(request.pin()),
                    now));
            CustomerDevice device = devices.saveAndFlush(new CustomerDevice(UuidV7.next(), tenant.id(), customer.id(),
                    deviceKey, deviceName(request.deviceName()), platform(request.platform()), now));
            auditService.record(AuditEvent.builder("MOBILE_BANKING_SIGNED_UP", PinService.RESOURCE)
                    .actor(ActorType.CUSTOMER, customer.id(), customer.customerNumber())
                    .resourceId(customer.id())
                    .resourceReference(customer.customerNumber())
                    .metadata("deviceId", device.getId())
                    .metadata("challengeId", parsed.challengeId())
                    .build());
            inbox.securityNotice(customer.id(), credential.getUsername(), tenant.displayName(), "Welcome",
                    "you have started signing up for mobile banking. If this was not you, contact us at once.");
            return Outcome.success(signIn(tenant, credential, customer, device, now));
        }).tokensOrThrow();
    }

    // ------------------------------------------------------------------------------------------- password

    /**
     * Texts a code for a password reset when the number has mobile banking. The answer looks the same either way.
     */
    public ChannelDtos.CodeSent startPasswordReset(ChannelDtos.PasswordReset request) {
        String deviceKey = requireDeviceKey();
        TenantSummary tenant = institution(request.institutionCode());
        String phone = PhoneNumbers.normalize(request.phoneNumber(), tenant.countryCode())
                .orElseThrow(() -> new BankingException(ChannelErrorCode.INVALID_PHONE_NUMBER));
        rateLimiter.check("customer-reset:" + fingerprint(phone));
        return TenantContext.callAs(tenant.id(), () -> transactionTemplate.execute(status -> {
            requireChannelEnabled();
            Optional<CustomerCredential> credential = credentials.lockByUsername(tenant.id(), phone)
                    .filter(CustomerCredential::isActive);
            OtpService.Issued issued = credential.isPresent()
                    ? otp.send(tenant.id(), credential.get().getCustomerId(), phone,
                    OtpChallenge.Purpose.PASSWORD_RESET, deviceKey, tenant.displayName())
                    : otp.decoy(tenant.id(), phone, OtpChallenge.Purpose.PASSWORD_RESET, deviceKey);
            return new ChannelDtos.CodeSent(issued.token(), issued.maskedPhone(), issued.expiresAt());
        }));
    }

    /**
     * Sets a new password with the texted code and the transaction PIN, and ends every session.
     */
    public void completePasswordReset(ChannelDtos.PasswordResetCompletion request) {
        String deviceKey = requireDeviceKey();
        ChallengeTokens.Parsed parsed = parseChallenge(request.challengeToken());
        TenantSummary tenant = challengeTenant(parsed);
        inTenant(tenant, () -> {
            requireChannelEnabled();
            Optional<String> phone = otp.phoneOf(tenant.id(), parsed.challengeId());
            String currentHash = phone.flatMap(number -> credentials.lockByUsername(tenant.id(), number))
                    .map(CustomerCredential::passwordHash).orElse(null);
            passwordService.validateNewPassword(request.newPassword(), phone.orElse(null), currentHash);
            Optional<OtpChallenge> challenge = otp.verify(tenant.id(), parsed.challengeId(), request.code(),
                    OtpChallenge.Purpose.PASSWORD_RESET, deviceKey);
            if (challenge.isEmpty()) {
                return Outcome.failure(ChannelErrorCode.CODE_INVALID);
            }
            CustomerCredential credential = credentials.lockByCustomerId(tenant.id(), challenge.get().getCustomerId())
                    .filter(CustomerCredential::isActive).orElse(null);
            if (credential == null) {
                return Outcome.failure(IamErrorCode.ACCOUNT_DISABLED);
            }
            PinService.Check pin = pinService.check(credential, request.pin());
            if (pin != PinService.Check.RIGHT) {
                credentials.save(credential);
                return Outcome.failure(pin == PinService.Check.LOCKED
                        ? new BankingException(ChannelErrorCode.PIN_LOCKED) : pinService.wrongPin(credential));
            }
            credential.changePassword(passwordService.hash(request.newPassword()), clock.instant());
            credentials.save(credential);
            customerSessions.revokeAll(credential.getCustomerId(), SessionService.REASON_PASSWORD_CHANGED);
            auditService.record(AuditEvent.builder("PASSWORD_RESET", PinService.RESOURCE)
                    .actor(ActorType.CUSTOMER, credential.getCustomerId(), null)
                    .resourceId(credential.getCustomerId())
                    .build());
            inbox.securityNotice(credential.getCustomerId(), credential.getUsername(), tenant.displayName(),
                    "Password reset", "your mobile banking password was reset. If this was not you, contact us at"
                            + " once.");
            return Outcome.success(null);
        }).throwIfFailed();
    }

    /**
     * The signed-in customer changes their password: every other session ends, and this one is replaced.
     */
    public TokenResponse changePassword(ChannelDtos.PasswordChange request) {
        AuthenticatedActor actor = requireCustomer();
        String deviceKey = requireDeviceKey();
        TenantSummary tenant = tenantService.findById(actor.tenantId()).orElseThrow();
        Outcome outcome = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            CustomerCredential credential = credentials.lockByCustomerId(actor.tenantId(), actor.id())
                    .orElseThrow(() -> new BankingException(CommonErrorCode.UNAUTHENTICATED));
            Outcome refused = checkPassword(credential, request.currentPassword(), tenant, now);
            if (refused != null) {
                return refused;
            }
            passwordService.validateNewPassword(request.newPassword(), credential.getUsername(),
                    credential.passwordHash());
            CustomerDevice device = devices.findTrusted(actor.tenantId(), actor.id(), deviceKey)
                    .orElseThrow(() -> new BankingException(CommonErrorCode.UNAUTHENTICATED));
            CustomerSummary customer = signInAllowed(credential)
                    .orElseThrow(() -> new BankingException(IamErrorCode.ACCOUNT_DISABLED));
            credential.changePassword(passwordService.hash(request.newPassword()), now);
            customerSessions.revokeAll(actor.id(), SessionService.REASON_PASSWORD_CHANGED);
            auditService.record(AuditEvent.builder("PASSWORD_CHANGED", PinService.RESOURCE)
                    .resourceId(actor.id())
                    .build());
            inbox.securityNotice(actor.id(), credential.getUsername(), tenant.displayName(), "Password changed",
                    "your mobile banking password was changed. If this was not you, contact us at once.");
            return Outcome.success(signIn(tenant, credential, customer, device, now));
        });
        return outcome.tokensOrThrow();
    }

    // -------------------------------------------------------------------------------------------- helpers

    private TokenResponse signIn(TenantSummary tenant, CustomerCredential credential, CustomerSummary customer,
                                 CustomerDevice device, Instant now) {
        credential.registerSuccessfulLogin(now);
        credentials.save(credential);
        TokenResponse tokens = customerSessions.open(tenant.id(), credential.getCustomerId(),
                customer.customerNumber(), device.getId());
        auditService.record(AuditEvent.builder("LOGIN_SUCCEEDED", AUTH_RESOURCE)
                .actor(ActorType.CUSTOMER, credential.getCustomerId(), customer.customerNumber())
                .resourceId(credential.getCustomerId())
                .metadata("deviceId", device.getId())
                .build());
        return tokens;
    }

    /**
     * @return null when the password is right and the sign-in not locked
     */
    private Outcome checkPassword(CustomerCredential credential, String password, TenantSummary tenant,
                                  Instant now) {
        if (credential.isLocked(now)) {
            passwordService.burnDummyCheck(password);
            auditService.record(loginFailure(credential.getCustomerId(), "ACCOUNT_LOCKED").build());
            return Outcome.failure(IamErrorCode.ACCOUNT_LOCKED);
        }
        if (passwordService.matches(password, credential.passwordHash())) {
            credential.resetFailedLogins(now);
            return null;
        }
        boolean lockedNow = credential.registerFailedLogin(securityProperties.lockout().maxFailedAttempts(),
                securityProperties.lockout().duration(), now);
        credentials.save(credential);
        auditService.record(loginFailure(credential.getCustomerId(), "BAD_PASSWORD").build());
        if (lockedNow) {
            auditService.record(AuditEvent.builder("ACCOUNT_LOCKED", AUTH_RESOURCE)
                    .actor(ActorType.CUSTOMER, credential.getCustomerId(), null)
                    .resourceId(credential.getCustomerId())
                    .outcome(AuditOutcome.DENIED)
                    .metadata("lockedUntil", credential.getLockedUntil())
                    .build());
            inbox.securityNotice(credential.getCustomerId(), credential.getUsername(), tenant.displayName(),
                    "Sign-in locked", "your mobile banking sign-in was locked after several wrong passwords. If this"
                            + " was not you, contact us.");
            return Outcome.failure(IamErrorCode.ACCOUNT_LOCKED);
        }
        return Outcome.failure(IamErrorCode.INVALID_CREDENTIALS);
    }

    /**
     * The customer may bank digitally: credential enabled and customer active, dormant or restricted (what they may
     * then do with their accounts is up to the accounts' own rules).
     */
    private Optional<CustomerSummary> signInAllowed(CustomerCredential credential) {
        if (!credential.isActive()) {
            auditService.record(loginFailure(credential.getCustomerId(), "CREDENTIAL_DISABLED").build());
            return Optional.empty();
        }
        Optional<CustomerSummary> customer = customerService.findForChannel(credential.getCustomerId())
                .filter(found -> SIGN_IN_STATUSES.contains(found.status()));
        if (customer.isEmpty()) {
            auditService.record(loginFailure(credential.getCustomerId(), "CUSTOMER_NOT_ACTIVE").build());
        }
        return customer;
    }

    private Outcome inTenant(TenantSummary tenant, Supplier<Outcome> work) {
        return TenantContext.callAs(tenant.id(), () -> transactionTemplate.execute(status -> work.get()));
    }

    private void requireChannelEnabled() {
        if (!institutionService.isFeatureEnabled(MOBILE_FEATURE)) {
            throw new BankingException(ChannelErrorCode.CHANNEL_NOT_ENABLED);
        }
    }

    private TenantSummary institution(String code) {
        return tenantService.findByCode(normalizeCode(code)).filter(TenantSummary::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Institution"));
    }

    private TenantSummary challengeTenant(ChallengeTokens.Parsed parsed) {
        rateLimiter.check("customer-code:" + parsed.challengeId());
        return tenantService.findById(parsed.tenantId()).filter(TenantSummary::isActive)
                .orElseThrow(() -> new BankingException(ChannelErrorCode.CODE_INVALID));
    }

    private static ChallengeTokens.Parsed parseChallenge(String token) {
        return ChallengeTokens.parse(token).orElseThrow(() -> new BankingException(ChannelErrorCode.CODE_INVALID));
    }

    static String requireDeviceKey() {
        String deviceKey = RequestMetadata.current().deviceId();
        if (deviceKey == null || deviceKey.isBlank()) {
            throw new BankingException(ChannelErrorCode.DEVICE_ID_REQUIRED);
        }
        return deviceKey.trim();
    }

    static AuthenticatedActor requireCustomer() {
        AuthenticatedActor actor = CurrentActor.require();
        if (actor.type() != ActorType.CUSTOMER || actor.id() == null) {
            throw new BankingException(CommonErrorCode.ACCESS_DENIED);
        }
        return actor;
    }

    /**
     * A name for a new device: what the app says it is, else its user agent, without control characters.
     */
    private static String deviceName(String requested) {
        String name = requested != null && !requested.isBlank() ? requested : RequestMetadata.current().userAgent();
        if (name == null || name.isBlank()) {
            return "Mobile device";
        }
        String clean = name.replaceAll("\\p{Cntrl}", " ").trim();
        return clean.length() <= DEVICE_NAME_MAX ? clean : clean.substring(0, DEVICE_NAME_MAX);
    }

    private static String platform(String requested) {
        return requested == null || requested.isBlank() ? null : requested.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * A non-reversible key for rate limiting, so phone numbers are not stored in the limiter.
     */
    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).replaceAll("[\\s().-]", "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static AuditEvent.Builder loginFailure(UUID customerId, String reason) {
        return AuditEvent.builder("LOGIN_FAILED", AUTH_RESOURCE)
                .actor(customerId == null ? ActorType.ANONYMOUS : ActorType.CUSTOMER, customerId, null)
                .outcome(AuditOutcome.FAILURE)
                .metadata("reason", reason)
                .metadata("channel", "CUSTOMER_APP");
    }

    /**
     * What a credential exchange came to. Failures are returned rather than thrown inside the transaction, so the
     * attempt counters they changed are committed.
     */
    record Outcome(TokenResponse tokens, BankingException failure) {

        static Outcome success(TokenResponse tokens) {
            return new Outcome(tokens, null);
        }

        static Outcome failure(ErrorCode code) {
            return new Outcome(null, new BankingException(code));
        }

        static Outcome failure(BankingException exception) {
            return new Outcome(null, exception);
        }

        TokenResponse tokensOrThrow() {
            throwIfFailed();
            return tokens;
        }

        void throwIfFailed() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
