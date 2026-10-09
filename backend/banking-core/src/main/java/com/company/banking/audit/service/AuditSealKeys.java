package com.company.banking.audit.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Signs and checks seal hashes with the configured HMAC keys. The application refuses to start without a valid
 * active key.
 */
@Component
public class AuditSealKeys {

    private static final String ALGORITHM = "HmacSHA256";

    private final int activeVersion;
    private final Map<Integer, SecretKeySpec> keys = new HashMap<>();

    public AuditSealKeys(AuditSealProperties properties) {
        if (properties.keys() == null || properties.keys().isEmpty()) {
            throw new IllegalStateException("No audit seal keys configured (banking.audit.seal.keys)");
        }
        properties.keys().forEach((version, encoded) -> {
            if (version < 1) {
                throw new IllegalStateException("Audit seal key versions start at 1");
            }
            keys.put(version, new SecretKeySpec(decode(encoded, version), ALGORITHM));
        });
        if (!keys.containsKey(properties.activeKeyVersion())) {
            throw new IllegalStateException("Active audit seal key version " + properties.activeKeyVersion()
                    + " is not configured");
        }
        this.activeVersion = properties.activeKeyVersion();
    }

    public int activeVersion() {
        return activeVersion;
    }

    public String sign(String sealHash) {
        return sign(activeVersion, sealHash).orElseThrow();
    }

    /**
     * Whether {@code signature} is the signature of {@code sealHash} with key {@code version}; empty when that key
     * is no longer configured.
     */
    public Optional<Boolean> verify(int version, String sealHash, String signature) {
        return sign(version, sealHash).map(expected -> MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII)));
    }

    private Optional<String> sign(int version, String sealHash) {
        SecretKeySpec key = keys.get(version);
        if (key == null) {
            return Optional.empty();
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return Optional.of(HexFormat.of().formatHex(mac.doFinal(sealHash.getBytes(StandardCharsets.US_ASCII))));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Audit seal signing failed", ex);
        }
    }

    private static byte[] decode(String encoded, int version) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("Missing audit seal key v" + version);
        }
        byte[] key = Base64.getDecoder().decode(encoded.trim());
        if (key.length < 32) {
            throw new IllegalStateException("Audit seal key v" + version + " must be at least 256 bits");
        }
        return key;
    }
}
