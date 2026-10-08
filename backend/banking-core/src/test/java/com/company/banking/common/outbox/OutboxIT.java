package com.company.banking.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.support.Fixtures.TenantHandle;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(OutboxIT.HandlerConfig.class)
class OutboxIT extends LedgerIntegrationTest {

    @TestConfiguration
    static class HandlerConfig {

        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }
    }

    static class RecordingHandler implements OutboxEventHandler {

        final List<OutboxMessage> received = new CopyOnWriteArrayList<>();
        volatile boolean failing;

        @Override
        public boolean supports(String eventType) {
            return eventType.startsWith("TEST_");
        }

        @Override
        public void handle(OutboxMessage message) {
            if (failing) {
                throw new IllegalStateException("Receiver unavailable");
            }
            received.add(message);
        }
    }

    @Autowired
    private OutboxService outbox;

    @Autowired
    private RecordingHandler handler;

    @Autowired
    private JdbcClient jdbcClient;

    private TenantHandle tenant;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        handler.received.clear();
        handler.failing = false;
    }

    @Test
    void eventsAreDeliveredAfterTheBusinessTransactionCommits() {
        UUID aggregate = UUID.randomUUID();
        inTenant(tenant.id(), () -> outbox.publish("TEST", aggregate, "TEST_HAPPENED", Map.of("amount", "10.00")));

        assertThat(inTenant(tenant.id(), () -> outbox.relayPending(10))).isEqualTo(1);
        assertThat(inTenant(tenant.id(), () -> outbox.relayPending(10))).isZero();

        assertThat(handler.received).singleElement().satisfies(message -> {
            assertThat(message.aggregateId()).isEqualTo(aggregate);
            assertThat(message.tenantId()).isEqualTo(tenant.id());
            assertThat(jsonMapper.readTree(message.payloadJson()).get("amount").asString()).isEqualTo("10.00");
        });
    }

    @Test
    void anEventFromARolledBackTransactionNeverExists() {
        assertThatThrownBy(() -> inTenant(tenant.id(), () -> {
            outbox.publish("TEST", UUID.randomUUID(), "TEST_HAPPENED", Map.of());
            throw new IllegalStateException("business failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(inTenant(tenant.id(), () -> outbox.relayPending(10))).isZero();
        assertThat(handler.received).isEmpty();
    }

    @Test
    void aFailedDeliveryIsRetriedLaterWithBackoff() {
        handler.failing = true;
        inTenant(tenant.id(), () -> outbox.publish("TEST", UUID.randomUUID(), "TEST_HAPPENED", Map.of()));

        assertThat(inTenant(tenant.id(), () -> outbox.relayPending(10))).isZero();
        handler.failing = false;
        assertThat(inTenant(tenant.id(), () -> outbox.relayPending(10))).as("not due yet").isZero();

        Map<String, Object> row = inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT attempts, last_error, next_attempt_at > now() AS deferred, published_at IS NULL AS pending"
                                + " FROM core.outbox_event")
                .query().singleRow());
        assertThat(row).containsEntry("attempts", 1).containsEntry("deferred", true).containsEntry("pending", true);
        assertThat(row.get("last_error")).isEqualTo(IllegalStateException.class.getName());
    }

    @Test
    void backoffDoublesUpToAnHour() {
        assertThat(OutboxService.backoff(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(OutboxService.backoff(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(OutboxService.backoff(5)).isEqualTo(Duration.ofSeconds(160));
        assertThat(OutboxService.backoff(30)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void tenantsSeeOnlyTheirOwnEvents() {
        inTenant(tenant.id(), () -> outbox.publish("TEST", UUID.randomUUID(), "TEST_HAPPENED", Map.of()));
        TenantHandle other = fixtures.onboardTenant();

        assertThat(inTenant(other.id(), () -> outbox.relayPending(10))).isZero();
        assertThat(inTenant(tenant.id(), () -> outbox.relayPending(10))).isEqualTo(1);
    }
}
