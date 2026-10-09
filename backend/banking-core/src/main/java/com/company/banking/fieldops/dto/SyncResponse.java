package com.company.banking.fieldops.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The outcome of every item of a sync, and what the server still misses from the device.
 *
 * @param highestSequenceNo the highest collection number the server has from the device
 * @param missing           numbers below it the server never received; the device should send them if it still
 *                          has them
 */
public record SyncResponse(List<CollectionResult> collections, List<VisitResult> visits, long highestSequenceNo,
                           List<Range> missing) {

    /**
     * {@code POSTED}: credited now. {@code DUPLICATE}: already received; the original outcome is repeated.
     * {@code REJECTED}: recorded but not credited ({@code code} says why; the cash is still with the officer).
     * {@code CONFLICT}: a different collection already used this reference or number; nothing was done and a
     * supervisor was alerted.
     *
     * @param originalStatus for a duplicate, what happened the first time ({@code POSTED} or {@code REJECTED})
     * @param balanceAfter   the account's ledger balance right after posting (only when posted now)
     */
    public record CollectionResult(UUID clientReference, String status, String originalStatus, String code,
                                   String message, UUID collectionId, String transactionReference,
                                   LocalDate businessDate, BigDecimal balanceAfter) {
    }

    /**
     * {@code RECORDED}, {@code DUPLICATE}, {@code REJECTED} (not stored) or {@code CONFLICT}.
     */
    public record VisitResult(UUID clientReference, String status, String code, String message) {
    }

    public record Range(long from, long to) {
    }
}
