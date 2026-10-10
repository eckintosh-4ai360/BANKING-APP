package com.company.banking.channel.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Customer channel settings ({@code banking.channel}).
 *
 * @param pinPepper      base64 key (at least 32 bytes) a PIN is keyed with before it is hashed; from the secret
 *                       manager, never stored in the database
 * @param otpPepper      base64 key (at least 32 bytes) one-time codes are hashed with
 * @param otpTtl         how long a texted code stays valid
 * @param otpMaxAttempts wrong answers a code allows before it fails
 * @param otpMaxPerHour  codes a phone number may be sent per hour
 * @param pinMaxAttempts wrong PINs in a row before the PIN locks (it then needs a reset)
 */
@ConfigurationProperties(prefix = "banking.channel")
public record ChannelProperties(String pinPepper, String otpPepper, Duration otpTtl, int otpMaxAttempts,
                                int otpMaxPerHour, int pinMaxAttempts) {
}
