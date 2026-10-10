package com.company.banking.channel.service;

import com.company.banking.channel.entity.OtpChallenge;
import com.company.banking.channel.exception.ChannelErrorCode;
import com.company.banking.channel.repository.OtpChallengeRepository;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.text.PhoneNumbers;
import com.company.banking.notification.service.SmsGateway;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * One-time codes texted to a customer's phone. A code is 6 random digits, kept only as a keyed hash bound to its
 * challenge, valid for minutes, for a few attempts and for one use; a phone gets a limited number per hour. A code
 * that cannot be texted fails the request (nothing pretends it was sent); security notices go out after commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository challenges;
    private final ChannelSecrets secrets;
    private final ChannelProperties properties;
    private final SmsGateway sms;
    private final Clock clock;

    record Issued(UUID tenantId, UUID challengeId, Instant expiresAt, String maskedPhone) {

        String token() {
            return ChallengeTokens.format(tenantId, challengeId);
        }
    }

    /**
     * Creates a challenge for the customer and texts its code.
     *
     * @param institution how the institution is named in the message
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Issued send(UUID tenantId, UUID customerId, String phone, OtpChallenge.Purpose purpose, String deviceKey,
                String institution) {
        Instant now = clock.instant();
        requireBelowHourlyLimit(tenantId, phone, now);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        UUID id = UuidV7.next();
        OtpChallenge challenge = challenges.save(new OtpChallenge(id, tenantId, Objects.requireNonNull(customerId),
                phone, purpose, deviceKey, secrets.otpHash(id, code), properties.otpMaxAttempts(), now,
                now.plus(properties.otpTtl())));
        sms.send(phone, institution + ": your code " + reason(purpose) + " is " + code + ". It expires in "
                + minutes(properties.otpTtl()) + " minutes. Never share it with anyone, including our staff.");
        return issued(challenge);
    }

    /**
     * A challenge for a request that matched no one (or no one who may get a code): it looks the same to the caller,
     * but nothing is sent and it can never be answered, so the answer does not reveal who is a customer.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Issued decoy(UUID tenantId, String phone, OtpChallenge.Purpose purpose, String deviceKey) {
        Instant now = clock.instant();
        requireBelowHourlyLimit(tenantId, phone, now);
        OtpChallenge challenge = challenges.save(new OtpChallenge(UuidV7.next(), tenantId, null, phone, purpose,
                deviceKey, null, properties.otpMaxAttempts(), now, now.plus(properties.otpTtl())));
        return issued(challenge);
    }

    /**
     * The phone an open challenge was sent to, without touching it (to check a new password against it first).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<String> phoneOf(UUID tenantId, UUID challengeId) {
        return challenges.findById(challengeId)
                .filter(challenge -> challenge.getTenantId().equals(tenantId) && challenge.isOpen(clock.instant()))
                .map(OtpChallenge::getPhone);
    }

    /**
     * Checks an answer. A wrong answer counts against the challenge (and fails it when the attempts run out); the
     * caller must commit even then, so failures are returned, never thrown.
     *
     * @param deviceKey the installation answering; it must be the one the code was asked for (when one was)
     * @return the verified challenge, used up
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<OtpChallenge> verify(UUID tenantId, UUID challengeId, String code, OtpChallenge.Purpose purpose,
                                  String deviceKey) {
        Instant now = clock.instant();
        OtpChallenge challenge = challenges.lock(tenantId, challengeId).orElse(null);
        if (challenge == null || challenge.getPurpose() != purpose || !challenge.isOpen(now)) {
            return Optional.empty();
        }
        boolean rightDevice = challenge.getDeviceKey() == null || challenge.getDeviceKey().equals(deviceKey);
        boolean rightCode = code != null && code.matches("^[0-9]{6}$")
                && ChannelSecrets.sameHash(challenge.codeHash(), secrets.otpHash(challengeId, code));
        if (!rightDevice || !rightCode) {
            challenge.registerWrongCode();
            challenges.save(challenge);
            return Optional.empty();
        }
        challenge.markVerified(now);
        challenges.save(challenge);
        return Optional.of(challenge);
    }

    /**
     * Texts a notice (no code) once the current transaction commits. A notice that cannot be sent is logged, never
     * fatal: it must not undo what it reports.
     */
    void noticeAfterCommit(String phone, String message) {
        afterCommit(() -> {
            try {
                sms.send(phone, message);
            } catch (RuntimeException ex) {
                log.warn("A security notice could not be texted: {}", ex.getMessage());
            }
        });
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void requireBelowHourlyLimit(UUID tenantId, String phone, Instant now) {
        if (challenges.countByTenantIdAndPhoneAndCreatedAtAfter(tenantId, phone, now.minus(Duration.ofHours(1)))
                >= properties.otpMaxPerHour()) {
            throw new BankingException(ChannelErrorCode.TOO_MANY_CODES);
        }
    }

    private static Issued issued(OtpChallenge challenge) {
        return new Issued(challenge.getTenantId(), challenge.getId(), challenge.getExpiresAt(),
                PhoneNumbers.mask(challenge.getPhone()));
    }

    private static String reason(OtpChallenge.Purpose purpose) {
        return switch (purpose) {
            case ACTIVATION -> "to set up mobile banking";
            case DEVICE_BINDING -> "to sign in on a new device";
            case PASSWORD_RESET -> "to reset your mobile banking password";
            case PIN_RESET -> "to reset your transaction PIN";
        };
    }

    private static long minutes(Duration ttl) {
        return Math.max(1, ttl.toMinutes());
    }
}
