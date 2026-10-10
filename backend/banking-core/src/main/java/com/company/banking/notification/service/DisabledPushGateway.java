package com.company.banking.notification.service;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No push provider is configured ({@code provider: none}): nothing is pushed; notifications stay in the in-app inbox.
 */
@Component
@ConditionalOnProperty(prefix = "banking.notification.push", name = "provider", havingValue = "none",
        matchIfMissing = true)
public class DisabledPushGateway implements PushGateway {

    @Override
    public void send(String provider, String token, String title, String body, Map<String, String> data) {
        // Deliberately nothing: the inbox holds the notification.
    }
}
