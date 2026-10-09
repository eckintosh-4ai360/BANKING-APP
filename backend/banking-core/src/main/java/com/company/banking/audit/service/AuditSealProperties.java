package com.company.banking.audit.service;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code banking.audit.seal.*}: how the audit trail is sealed.
 *
 * @param activeKeyVersion version of the key that signs new seals
 * @param keys             base64-encoded HMAC-SHA256 keys (at least 256 bits) by version, from the secret manager.
 *                         Old versions stay configured for as long as their seals must be verifiable.
 * @param period           length of one seal; periods are aligned to the epoch (an hour starts on the hour)
 * @param lag              how long after a period ends it is sealed, so audit rows stamped just before the end
 *                         can still be written
 * @param maxPerRun        seals per scope and run, so catching up after downtime is spread over several runs
 */
@ConfigurationProperties("banking.audit.seal")
public record AuditSealProperties(
        int activeKeyVersion,
        Map<Integer, String> keys,
        @DefaultValue("PT1H") Duration period,
        @DefaultValue("PT5M") Duration lag,
        @DefaultValue("168") int maxPerRun) {
}
