package com.company.banking.channel.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Keyed hashes for the customer channel. A PIN is keyed with the PIN pepper before Argon2id, so a leaked database
 * alone cannot be searched through the few PIN values (spec review S1). One-time codes are kept only as a keyed hash
 * bound to their challenge. Both keys come from configuration (the secret manager); the application refuses to start
 * without them.
 */
@Component
class ChannelSecrets {

    private static final String HMAC = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec pinKey;
    private final SecretKeySpec otpKey;

    ChannelSecrets(ChannelProperties properties) {
        this.pinKey = key(properties.pinPepper(), "banking.channel.pin-pepper");
        this.otpKey = key(properties.otpPepper(), "banking.channel.otp-pepper");
    }

    /**
     * The PIN as it goes to the password hasher.
     */
    String keyedPin(String pin) {
        return hmacHex(pinKey, pin);
    }

    String otpHash(UUID challengeId, String code) {
        return hmacHex(otpKey, challengeId + ":" + code);
    }

    static boolean sameHash(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hmacHex(SecretKeySpec key, String value) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC-SHA256 is not available", ex);
        }
    }

    private static SecretKeySpec key(String base64, String property) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(property + " is not configured");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(property + " is not valid base64", ex);
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(property + " must be at least " + MIN_KEY_BYTES + " bytes");
        }
        return new SecretKeySpec(bytes, HMAC);
    }
}
