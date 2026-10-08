package com.company.banking.common.outbox;

/**
 * Receives outbox events after the business transaction committed (notifications, webhooks, integrations).
 * Delivery is at least once: a handler must tolerate seeing the same event again (use the event id).
 */
public interface OutboxEventHandler {

    boolean supports(String eventType);

    /**
     * Throwing marks the delivery as failed; the event is retried with backoff.
     */
    void handle(OutboxMessage message);
}
