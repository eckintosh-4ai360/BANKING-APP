package com.company.banking.notification.service;

import java.util.Map;

/**
 * Sends push notifications to an app installation (Firebase Cloud Messaging, Apple Push Notification service).
 * Provider adapters are chosen by {@code banking.notification.push.provider}; credentials for them come from the
 * secret manager and arrive with the integrations of Phase 7.
 *
 * <p>Push is a convenience: the in-app inbox holds every notification, so a push that cannot be sent is never an
 * error for the caller.
 */
public interface PushGateway {

    /**
     * @param provider {@code FCM} or {@code APNS}
     * @param data     small key-value hints for the app (e.g. what to open); never secrets or balances
     */
    void send(String provider, String token, String title, String body, Map<String, String> data);
}
