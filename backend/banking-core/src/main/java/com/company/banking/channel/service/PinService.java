package com.company.banking.channel.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditOutcome;
import com.company.banking.audit.service.AuditService;
import com.company.banking.channel.entity.CustomerCredential;
import com.company.banking.channel.exception.ChannelErrorCode;
import com.company.banking.channel.repository.CustomerCredentialRepository;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.iam.service.PasswordService;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction PINs: Argon2id over the PIN keyed with the pepper (never the PIN itself), checked with a failure
 * counter that locks the PIN after a few wrong attempts in a row. A locked PIN needs a reset (texted code and the
 * password). PINs are never logged and never returned.
 */
@Service
@RequiredArgsConstructor
public class PinService {

    static final String RESOURCE = "CUSTOMER_CREDENTIAL";

    public enum Check { RIGHT, WRONG, LOCKED }

    private final CustomerCredentialRepository credentials;
    private final ChannelSecrets secrets;
    private final PasswordService passwordService;
    private final ChannelProperties properties;
    private final AuditService auditService;
    private final Clock clock;

    String hash(String pin) {
        requireAcceptable(pin);
        return passwordService.hash(secrets.keyedPin(pin));
    }

    static void requireAcceptable(String pin) {
        if (!PinPolicy.isAcceptable(pin)) {
            throw new BankingException(ChannelErrorCode.WEAK_PIN);
        }
    }

    /**
     * Checks a PIN against a credential the caller has locked, counting failures; the caller must commit whatever
     * the outcome.
     */
    Check check(CustomerCredential credential, String pin) {
        if (credential.isPinLocked()) {
            passwordService.burnDummyCheck(String.valueOf(pin));
            return Check.LOCKED;
        }
        if (pin != null && passwordService.matches(secrets.keyedPin(pin), credential.pinHash())) {
            credential.registerRightPin(clock.instant());
            return Check.RIGHT;
        }
        boolean lockedNow = credential.registerWrongPin(properties.pinMaxAttempts(), clock.instant());
        auditService.record(AuditEvent.builder(lockedNow ? "PIN_LOCKED" : "PIN_WRONG", RESOURCE)
                .resourceId(credential.getCustomerId())
                .outcome(AuditOutcome.FAILURE)
                .metadata("failedAttempts", credential.getPinFailedAttempts())
                .build());
        return lockedNow ? Check.LOCKED : Check.WRONG;
    }

    /**
     * Confirms a money movement with the signed-in customer's PIN. The failure count is committed on its own, so a
     * refused payment still counts the wrong PIN.
     *
     * @throws BankingException {@code WRONG_PIN} or {@code PIN_LOCKED}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BankingException.class)
    public void requireForPayment(UUID customerId, String pin) {
        CustomerCredential credential = credentials.lockByCustomerId(TenantContext.requireTenantId(), customerId)
                .orElseThrow(() -> new BankingException(CommonErrorCode.ACCESS_DENIED));
        Check result = check(credential, pin);
        credentials.save(credential);
        switch (result) {
            case RIGHT -> {
            }
            case WRONG -> throw wrongPin(credential);
            case LOCKED -> throw new BankingException(ChannelErrorCode.PIN_LOCKED);
        }
    }

    BankingException wrongPin(CustomerCredential credential) {
        int left = properties.pinMaxAttempts() - credential.getPinFailedAttempts();
        return new BankingException(ChannelErrorCode.WRONG_PIN, "The transaction PIN is wrong. " + left
                + (left == 1 ? " attempt is" : " attempts are") + " left before it locks.");
    }
}
