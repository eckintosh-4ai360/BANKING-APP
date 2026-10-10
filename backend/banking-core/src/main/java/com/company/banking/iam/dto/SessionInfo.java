package com.company.banking.iam.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A sign-in session as its owner sees it (login history and session management).
 *
 * @param deviceId the customer's trusted device the session was opened on
 * @param current  the session making the request
 */
public record SessionInfo(UUID id, UUID deviceId, String status, Instant createdAt, Instant lastRefreshedAt,
                          Instant expiresAt, Instant revokedAt, String revokeReason, String ipAddress,
                          String userAgent, boolean current) {
}
