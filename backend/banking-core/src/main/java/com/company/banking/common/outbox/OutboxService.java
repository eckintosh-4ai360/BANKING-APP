package com.company.banking.common.outbox;

import com.company.banking.common.id.UuidV7;
import com.company.banking.common.tenant.TenantContext;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transactional outbox (decision D6): {@link #publish} writes the event in the business transaction, so it exists
 * exactly when the business change committed; {@link #relayPending} delivers it afterwards.
 */
@Slf4j
@Service
public class OutboxService {

    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final List<OutboxEventHandler> handlers;
    private final Clock clock;

    public OutboxService(JdbcClient jdbc, JsonMapper jsonMapper, List<OutboxEventHandler> handlers, Clock clock) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.handlers = List.copyOf(handlers);
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publish(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        UUID id = UuidV7.next();
        jdbc.sql("INSERT INTO core.outbox_event (id, tenant_id, aggregate_type, aggregate_id, event_type, payload,"
                        + " occurred_at, next_attempt_at)"
                        + " VALUES (:id, :tenantId, :aggregateType, :aggregateId, :eventType, CAST(:payload AS jsonb),"
                        + " :now, :now)")
                .param("id", uuid(id))
                .param("tenantId", uuid(TenantContext.requireTenantId()))
                .param("aggregateType", aggregateType)
                .param("aggregateId", uuid(aggregateId))
                .param("eventType", eventType)
                .param("payload", jsonMapper.writeValueAsString(payload))
                .param("now", Timestamp.from(clock.instant()))
                .update();
        return id;
    }

    /**
     * Delivers due events of the current tenant to every handler that supports them. Rows are claimed with
     * {@code SKIP LOCKED}, so several relays never deliver the same event at the same time.
     *
     * @return number of events handled successfully
     */
    @Transactional
    public int relayPending(int batchSize) {
        UUID tenantId = TenantContext.requireTenantId();
        Instant now = clock.instant();
        List<OutboxMessage> due = jdbc.sql("SELECT id, tenant_id, aggregate_type, aggregate_id, event_type,"
                        + " payload::text AS payload, occurred_at, attempts FROM core.outbox_event"
                        + " WHERE tenant_id = :tenantId AND published_at IS NULL AND next_attempt_at <= :now"
                        + " ORDER BY occurred_at, id LIMIT :limit FOR UPDATE SKIP LOCKED")
                .param("tenantId", uuid(tenantId))
                .param("now", Timestamp.from(now))
                .param("limit", batchSize)
                .query((rs, rowNum) -> new OutboxMessage(rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class), rs.getString("aggregate_type"),
                        rs.getObject("aggregate_id", UUID.class), rs.getString("event_type"), rs.getString("payload"),
                        rs.getTimestamp("occurred_at").toInstant(), rs.getInt("attempts")))
                .list();
        int delivered = 0;
        for (OutboxMessage message : due) {
            try {
                handlers.stream().filter(handler -> handler.supports(message.eventType()))
                        .forEach(handler -> handler.handle(message));
                jdbc.sql("UPDATE core.outbox_event SET published_at = :now, attempts = attempts + 1, last_error = NULL"
                                + " WHERE id = :id")
                        .param("id", uuid(message.id()))
                        .param("now", Timestamp.from(clock.instant()))
                        .update();
                delivered++;
            } catch (RuntimeException failure) {
                // Only the exception type is stored: messages can echo payload data.
                log.warn("Outbox event {} ({}) failed on attempt {}: {}", message.id(), message.eventType(),
                        message.attempts() + 1, failure.getClass().getSimpleName());
                jdbc.sql("UPDATE core.outbox_event SET attempts = attempts + 1, next_attempt_at = :next,"
                                + " last_error = :error WHERE id = :id")
                        .param("id", uuid(message.id()))
                        .param("next", Timestamp.from(clock.instant().plus(backoff(message.attempts() + 1))))
                        .param("error", failure.getClass().getName())
                        .update();
            }
        }
        return delivered;
    }

    /**
     * 10 s, 20 s, 40 s ... capped at one hour.
     */
    static Duration backoff(int attempts) {
        long seconds = 10L << Math.min(Math.max(attempts - 1, 0), 20);
        Duration delay = Duration.ofSeconds(seconds);
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static SqlParameterValue uuid(UUID value) {
        return new SqlParameterValue(Types.OTHER, value);
    }
}
