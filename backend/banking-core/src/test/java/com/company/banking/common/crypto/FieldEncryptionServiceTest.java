package com.company.banking.common.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldEncryptionServiceTest {

    private static final String KEY_1 = "BVOxNmlXHutcXPq9VPpsu97iDGWPvjp6GCFAdnrmrwM=";
    private static final String KEY_2 = "bN9YtA/MrxiGo85v+XFsh9dIl9SEyvMbkA+lTekXVT8=";
    private static final String BLIND = "ACtTDQ1zY8e+jYoxRHFUOd4MVP7IIIBM+9shtcgHaDc=";

    private final FieldEncryptionService service =
            new FieldEncryptionService(new CryptoProperties(1, Map.of(1, KEY_1), BLIND));

    @Test
    void encryptsAndDecryptsWithinTheSameContext() {
        String ciphertext = service.encrypt("GHA-123456789-0", "tenant/customer_identification/row/id");
        assertThat(ciphertext).startsWith("v1:").doesNotContain("123456789");
        assertThat(service.decrypt(ciphertext, "tenant/customer_identification/row/id"))
                .isEqualTo("GHA-123456789-0");
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        assertThat(service.encrypt("secret", "ctx")).isNotEqualTo(service.encrypt("secret", "ctx"));
    }

    @Test
    void ciphertextMovedToAnotherRowOrTenantCannotBeDecrypted() {
        String ciphertext = service.encrypt("GHA-123456789-0", "tenant-a/customer_identification/row-1/id");
        assertThatThrownBy(() -> service.decrypt(ciphertext, "tenant-b/customer_identification/row-1/id"))
                .isInstanceOf(FieldEncryptionService.DecryptionException.class)
                .hasMessageNotContaining("123456789");
        assertThatThrownBy(() -> service.decrypt(ciphertext, "tenant-a/customer_identification/row-2/id"))
                .isInstanceOf(FieldEncryptionService.DecryptionException.class);
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String ciphertext = service.encrypt("value", "ctx");
        char[] chars = ciphertext.toCharArray();
        int last = chars.length - 3;
        chars[last] = chars[last] == 'A' ? 'B' : 'A';
        assertThatThrownBy(() -> service.decrypt(new String(chars), "ctx")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void oldKeyVersionsRemainReadableAfterRotation() {
        String underVersion1 = service.encrypt("value", "ctx");
        FieldEncryptionService rotated = new FieldEncryptionService(
                new CryptoProperties(2, Map.of(1, KEY_1, 2, KEY_2), BLIND));
        assertThat(rotated.decrypt(underVersion1, "ctx")).isEqualTo("value");
        assertThat(rotated.encrypt("value", "ctx")).startsWith("v2:");
    }

    @Test
    void bytesRoundTrip() {
        byte[] document = "%PDF-1.7 fake".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = service.encryptBytes(document, "doc");
        assertThat(encrypted).isNotEqualTo(document);
        assertThat(service.decryptBytes(encrypted, "doc")).isEqualTo(document);
    }

    @Test
    void blindIndexIsDeterministicPerTenantButUnrelatedAcrossTenants() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String a1 = service.blindIndex(tenantA, "id_number:GHANA_CARD", "GHA1234567890");
        String a2 = service.blindIndex(tenantA, "id_number:GHANA_CARD", "GHA1234567890");
        String b = service.blindIndex(tenantB, "id_number:GHANA_CARD", "GHA1234567890");
        assertThat(a1).hasSize(64).isEqualTo(a2).isNotEqualTo(b);
    }

    @Test
    void refusesToStartWithoutValidKeys() {
        assertThatThrownBy(() -> new FieldEncryptionService(new CryptoProperties(1, Map.of(1, ""), BLIND)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FieldEncryptionService(new CryptoProperties(2, Map.of(1, KEY_1), BLIND)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FieldEncryptionService(new CryptoProperties(1, Map.of(1, "c2hvcnQ="), BLIND)))
                .isInstanceOf(IllegalStateException.class);
    }
}
