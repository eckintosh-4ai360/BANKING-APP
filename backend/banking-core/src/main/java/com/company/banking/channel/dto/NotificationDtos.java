package com.company.banking.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record Notification(UUID id, String category, String title, String body, String referenceType,
                               UUID referenceId, Instant createdAt, boolean read) {
    }

    public record Unread(long count) {
    }

    /**
     * Registers this installation for push notifications (the token the platform's push service gave the app).
     */
    public record PushRegistration(
            @NotBlank @Pattern(regexp = "^(FCM|APNS)$") String provider,
            @NotBlank @Size(max = 512) String token) {

        @Override
        public String toString() {
            return "PushRegistration[provider=" + provider + ", token=***]";
        }
    }

    /**
     * @param smsAlerts text alerts for money in and out (security notices are always texted)
     */
    public record Preferences(@NotNull Boolean smsAlerts) {
    }
}
