package com.company.banking.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.audit.repository.AuditLogRow;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditHashingTest {

    private static final HexFormat HEX = HexFormat.of();
    private static final UUID ID = UUID.fromString("0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
    private static final UUID TENANT = UUID.fromString("11111111-2222-4333-8444-555555555555");

    @Test
    void anEmptyTrailHasTheHashOfNothing() {
        assertThat(AuditHashing.merkleRoot(List.of()))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void theMerkleRootPairsNeighboursAndCarriesAnUnpairedNodeUp() {
        byte[] a = leaf("a");
        byte[] b = leaf("b");
        byte[] c = leaf("c");
        byte[] d = leaf("d");
        byte[] e = leaf("e");

        assertThat(AuditHashing.merkleRoot(List.of(a))).isEqualTo(HEX.formatHex(a));
        assertThat(AuditHashing.merkleRoot(List.of(a, b))).isEqualTo(HEX.formatHex(node(a, b)));
        assertThat(AuditHashing.merkleRoot(List.of(a, b, c))).isEqualTo(HEX.formatHex(node(node(a, b), c)));
        assertThat(AuditHashing.merkleRoot(List.of(a, b, c, d, e)))
                .isEqualTo(HEX.formatHex(node(node(node(a, b), node(c, d)), e)));
        assertThat(AuditHashing.merkleRoot(List.of(b, a))).as("order matters")
                .isNotEqualTo(AuditHashing.merkleRoot(List.of(a, b)));
    }

    @Test
    void aRowHashIsTheDocumentedEncoding() {
        AuditLogRow row = row("Ada Admin", null, "{\"status\": \"ACTIVE\"}");
        ByteBuffer expected = ByteBuffer.allocate(1024);
        expected.put((byte) 0);
        for (String field : new String[]{ID.toString(), TENANT.toString(), "1777777777123456", "STAFF", null,
                "Ada Admin", "ACCOUNT_DORMANT", "SUCCESS", "ACCOUNT", "42", null, null, null,
                "{\"status\": \"ACTIVE\"}", null, null, null, null, null}) {
            if (field == null) {
                expected.putInt(-1);
            } else {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                expected.putInt(bytes.length).put(bytes);
            }
        }
        byte[] encoded = new byte[expected.position()];
        expected.flip().get(encoded);

        assertThat(AuditHashing.rowHash(row)).isEqualTo(sha256(encoded));
    }

    @Test
    void everyFieldAndEveryBoundaryCounts() {
        byte[] original = AuditHashing.rowHash(row("Ada Admin", null, null));

        assertThat(AuditHashing.rowHash(row("Ada Admin", null, null))).isEqualTo(original);
        assertThat(AuditHashing.rowHash(row("Ada Admim", null, null))).isNotEqualTo(original);
        assertThat(AuditHashing.rowHash(row("Ada Admin", "", null))).as("empty is not null")
                .isNotEqualTo(original);
        assertThat(AuditHashing.rowHash(row("Ada", " Admin", null))).as("moving a boundary")
                .isNotEqualTo(AuditHashing.rowHash(row("Ada ", "Admin", null)));
    }

    @Test
    void theSealHashCoversTheScopeTheRangeAndTheChain() {
        Instant start = Instant.parse("2027-04-30T10:00:00Z");
        Instant end = Instant.parse("2027-04-30T11:00:00Z");
        String root = "ab".repeat(32);

        String hash = AuditHashing.sealHash(TENANT, 7, start, end, 12, root, AuditHashing.GENESIS);
        String header = "v1|" + TENANT + "|7|1809079200000000|1809082800000000|12|" + root + "|" + "0".repeat(64);
        assertThat(hash).isEqualTo(HEX.formatHex(sha256(header.getBytes(StandardCharsets.UTF_8))));
        assertThat(AuditHashing.sealHash(null, 7, start, end, 12, root, AuditHashing.GENESIS))
                .isEqualTo(HEX.formatHex(sha256(header.replace(TENANT.toString(), "platform")
                        .getBytes(StandardCharsets.UTF_8))));
        assertThat(AuditHashing.sealHash(TENANT, 7, start, end, 13, root, AuditHashing.GENESIS)).isNotEqualTo(hash);
    }

    @Test
    void timestampsCountInMicroseconds() {
        assertThat(AuditHashing.micros(Instant.parse("1970-01-01T00:00:01.000002999Z"))).isEqualTo(1_000_002);
    }

    private static AuditLogRow row(String actorName, String resourceReference, String afterState) {
        return new AuditLogRow(ID, TENANT, Instant.ofEpochSecond(1_777_777_777L, 123_456_000), "STAFF", null,
                actorName, "ACCOUNT_DORMANT", "SUCCESS", "ACCOUNT", "42", resourceReference, null, null, afterState,
                null, null, null, null, null);
    }

    private static byte[] leaf(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] node(byte[] left, byte[] right) {
        return sha256(ByteBuffer.allocate(65).put((byte) 1).put(left).put(right).array());
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
