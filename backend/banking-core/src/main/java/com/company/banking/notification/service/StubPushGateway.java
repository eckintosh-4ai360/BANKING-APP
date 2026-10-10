package com.company.banking.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * For development and tests: keeps the last pushes in memory instead of sending them.
 */
@Component
@ConditionalOnProperty(prefix = "banking.notification.push", name = "provider", havingValue = "stub")
public class StubPushGateway implements PushGateway {

    private static final int KEPT = 200;

    private final Deque<SentPush> sent = new ArrayDeque<>();
    private final Clock clock;

    public StubPushGateway(Clock clock) {
        this.clock = clock;
    }

    public record SentPush(String provider, String token, String title, String body, Map<String, String> data,
                           Instant sentAt) {
    }

    @Override
    public synchronized void send(String provider, String token, String title, String body,
                                  Map<String, String> data) {
        sent.addFirst(new SentPush(provider, token, title, body, Map.copyOf(data), clock.instant()));
        while (sent.size() > KEPT) {
            sent.removeLast();
        }
    }

    public synchronized List<SentPush> sentTo(String token) {
        return sent.stream().filter(push -> push.token().equals(token)).toList();
    }
}
