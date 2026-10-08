package com.company.banking.common.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * An event recorded in the business transaction, as handed to {@link OutboxEventHandler}s.
 *
 * @param payloadJson the event payload; never contains secrets (callers pass DTOs, not entities)
 */
public record OutboxMessage(
        UUID id,
        UUID tenantId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payloadJson,
        Instant occurredAt,
        int attempts) {
}
