package com.company.banking.customer.service;

import com.company.banking.common.crypto.FieldEncryptionService;
import com.company.banking.common.crypto.Masking;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Turns an identity or tax number into its three stored forms: ciphertext bound to its row, a blind index for
 * duplicate detection, and a masked display value. The plaintext is never persisted.
 */
@Component
@RequiredArgsConstructor
public class SensitiveValueProtector {

    private final FieldEncryptionService encryption;

    public Protected protect(UUID tenantId, String table, UUID rowId, String purpose, String rawValue) {
        String normalized = normalize(rawValue);
        return new Protected(
                normalized,
                encryption.encrypt(normalized, context(tenantId, table, rowId, purpose)),
                blindIndex(tenantId, purpose, normalized),
                Masking.maskIdentifier(normalized));
    }

    public String blindIndex(UUID tenantId, String purpose, String rawValue) {
        String comparable = normalize(rawValue).replaceAll("[^A-Z0-9]", "");
        return encryption.blindIndex(tenantId, purpose, comparable);
    }

    public String reveal(UUID tenantId, String table, UUID rowId, String purpose, String encrypted) {
        return encryption.decrypt(encrypted, context(tenantId, table, rowId, purpose));
    }

    /**
     * Upper-case, no whitespace. Separators such as hyphens are kept because some formats require them.
     */
    public static String normalize(String rawValue) {
        return rawValue.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String context(UUID tenantId, String table, UUID rowId, String purpose) {
        return tenantId + "/" + table + "/" + rowId + "/" + purpose;
    }

    /**
     * @param normalized only for validation in the calling method; never store or log it
     */
    public record Protected(String normalized, String encrypted, String blindIndex, String masked) {

        @Override
        public String toString() {
            return "Protected[masked=" + masked + "]";
        }
    }
}
