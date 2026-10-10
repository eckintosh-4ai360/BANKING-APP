package com.company.banking.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * For development and tests: keeps the last messages in memory instead of sending them (nothing is logged or
 * stored). Deployed environments refuse to start with it (see ProductionSafetyGuard).
 */
@Component
@ConditionalOnProperty(prefix = "banking.notification.sms", name = "provider", havingValue = "stub")
public class StubSmsGateway implements SmsGateway {

    private static final int KEPT = 200;

    private final Deque<SentMessage> sent = new ArrayDeque<>();
    private final Clock clock;

    public StubSmsGateway(Clock clock) {
        this.clock = clock;
    }

    public record SentMessage(String phoneNumber, String body, Instant sentAt) {

        @Override
        public String toString() {
            return "SentMessage[to=" + phoneNumber + ", body=***]";
        }
    }

    @Override
    public synchronized void send(String phoneNumber, String message) {
        sent.addFirst(new SentMessage(phoneNumber, message, clock.instant()));
        while (sent.size() > KEPT) {
            sent.removeLast();
        }
    }

    /**
     * Messages sent to a number, newest first.
     */
    public synchronized List<SentMessage> sentTo(String phoneNumber) {
        return sent.stream().filter(message -> message.phoneNumber().equals(phoneNumber)).toList();
    }

    public Optional<SentMessage> lastTo(String phoneNumber) {
        return sentTo(phoneNumber).stream().findFirst();
    }
}
