package com.company.banking.audit.dto;

import java.time.Instant;
import java.util.List;

/**
 * The result of checking the seals of a period against the audit trail as it is now.
 *
 * @param sealedThrough end of the trail's last seal; rows after it are not sealed yet
 * @param intact        every seal checked matches its rows, its chain and its signature
 */
public record AuditVerificationReport(
        Instant from,
        Instant to,
        int sealsChecked,
        long rowsChecked,
        Instant sealedThrough,
        boolean intact,
        List<Problem> problems) {

    /**
     * @param code {@code ROWS_CHANGED} (rows added, removed or altered), {@code SEAL_ALTERED} (the seal's own fields
     *             no longer give its hash), {@code CHAIN_BROKEN} (not linked to the previous seal),
     *             {@code SIGNATURE_INVALID}, or {@code KEY_UNAVAILABLE} (its key is no longer configured)
     */
    public record Problem(long sequenceNo, Instant rangeStart, Instant rangeEnd, String code, String detail) {
    }
}
