package com.company.banking.common.crypto;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Application-level encryption of sensitive fields and documents (AES-256-GCM) and keyed blind indexes
 * (HMAC-SHA256) for exact-match lookups on encrypted values.
 *
 * <p>Every ciphertext is bound to an <em>associated-data context</em> such as
 * {@code <tenant>/customer_identification/<row id>}: copying a ciphertext into another row, field or tenant makes
 * decryption fail. Blind indexes include the tenant, so the same national ID produces unrelated index values in two
 * institutions.
 *
 * <p>Formats: text {@code v<version>:<base64(iv || ciphertext || tag)>}; bytes
 * {@code [version:1][iv:12][ciphertext || tag]}.
 */
@Service
public class FieldEncryptionService {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final Map<Integer, SecretKey> dataKeys = new HashMap<>();
    private final int activeVersion;
    private final SecretKey blindIndexKey;

    public FieldEncryptionService(CryptoProperties properties) {
        if (properties.dataKeys() == null || properties.dataKeys().isEmpty()) {
            throw new IllegalStateException("No data encryption keys configured (banking.crypto.data-keys)");
        }
        properties.dataKeys().forEach((version, encoded) -> {
            if (version < 1 || version > 255) {
                throw new IllegalStateException("Data key versions must be between 1 and 255");
            }
            dataKeys.put(version, new SecretKeySpec(decodeKey(encoded, "data key v" + version), "AES"));
        });
        if (!dataKeys.containsKey(properties.activeKeyVersion())) {
            throw new IllegalStateException("Active data key version " + properties.activeKeyVersion()
                    + " is not configured");
        }
        this.activeVersion = properties.activeKeyVersion();
        this.blindIndexKey = new SecretKeySpec(decodeKey(properties.blindIndexKey(), "blind index key"),
                "HmacSHA256");
    }

    public String encrypt(String plaintext, String context) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        byte[] ciphertext = cipher(Cipher.ENCRYPT_MODE, dataKeys.get(activeVersion), iv, context)
                .doFinalUnchecked(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        return "v" + activeVersion + ":" + Base64.getEncoder().encodeToString(payload);
    }

    public String decrypt(String encrypted, String context) {
        if (encrypted == null) {
            return null;
        }
        int separator = encrypted.indexOf(':');
        if (!encrypted.startsWith("v") || separator < 2) {
            throw new IllegalArgumentException("Unrecognised ciphertext format");
        }
        int version = Integer.parseInt(encrypted.substring(1, separator));
        byte[] payload = Base64.getDecoder().decode(encrypted.substring(separator + 1));
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
        return new String(cipher(Cipher.DECRYPT_MODE, key(version), iv, context).doFinalUnchecked(ciphertext),
                StandardCharsets.UTF_8);
    }

    public byte[] encryptBytes(byte[] plaintext, String context) {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        byte[] ciphertext = cipher(Cipher.ENCRYPT_MODE, dataKeys.get(activeVersion), iv, context)
                .doFinalUnchecked(plaintext);
        return ByteBuffer.allocate(1 + iv.length + ciphertext.length)
                .put((byte) activeVersion).put(iv).put(ciphertext).array();
    }

    public byte[] decryptBytes(byte[] encrypted, String context) {
        int version = Byte.toUnsignedInt(encrypted[0]);
        byte[] iv = Arrays.copyOfRange(encrypted, 1, 1 + IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(encrypted, 1 + IV_LENGTH, encrypted.length);
        return cipher(Cipher.DECRYPT_MODE, key(version), iv, context).doFinalUnchecked(ciphertext);
    }

    /**
     * Deterministic keyed hash for equality search on an encrypted value. Callers must normalise the value first
     * (for example upper-case, no spaces) so equivalent inputs produce the same index.
     */
    public String blindIndex(UUID tenantId, String purpose, String normalizedValue) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(blindIndexKey);
            byte[] digest = mac.doFinal((tenantId + "|" + purpose + "|" + normalizedValue)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Blind index computation failed", ex);
        }
    }

    public int activeKeyVersion() {
        return activeVersion;
    }

    private SecretKey key(int version) {
        SecretKey key = dataKeys.get(version);
        if (key == null) {
            throw new IllegalStateException("Data key version " + version + " is not configured");
        }
        return key;
    }

    private static CipherOperation cipher(int mode, SecretKey key, byte[] iv, String context) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new CipherOperation(cipher);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cipher initialisation failed", ex);
        }
    }

    private static byte[] decodeKey(String encoded, String name) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("Missing " + name);
        }
        byte[] key = Base64.getDecoder().decode(encoded.trim());
        if (key.length != 32) {
            throw new IllegalStateException(name + " must be 256 bits (32 bytes, base64-encoded)");
        }
        return key;
    }

    private record CipherOperation(Cipher cipher) {
        byte[] doFinalUnchecked(byte[] input) {
            try {
                return cipher.doFinal(input);
            } catch (GeneralSecurityException ex) {
                throw new DecryptionException(ex);
            }
        }
    }

    /**
     * Wrong key, tampered data or mismatched context. Never includes the data in its message.
     */
    public static class DecryptionException extends IllegalStateException {
        DecryptionException(Throwable cause) {
            super("Encrypted value could not be processed (wrong key, context or tampered data)", cause);
        }
    }
}
