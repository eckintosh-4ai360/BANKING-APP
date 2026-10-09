package com.company.banking.audit.service;

import com.company.banking.audit.repository.AuditLogRow;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * The hashes behind audit seals, defined precisely enough to verify a seal without this code.
 *
 * <ul>
 *   <li><b>Row hash:</b> SHA-256 of {@code 0x00} followed by every column of the row in table order, each as a
 *   4-byte big-endian length and its UTF-8 bytes ({@code -1} and no bytes for NULL). UUIDs are lower-case text,
 *   {@code occurred_at} is microseconds since the epoch in decimal, JSON columns are PostgreSQL's {@code jsonb}
 *   text.</li>
 *   <li><b>Merkle root:</b> rows in {@code (occurred_at, id)} order; each level pairs neighbours as SHA-256 of
 *   {@code 0x01 || left || right} and carries an unpaired last node up unchanged. No rows: SHA-256 of nothing.</li>
 *   <li><b>Seal hash:</b> SHA-256 of the UTF-8 text
 *   {@code v1|scope|sequence|start micros|end micros|row count|merkle root|previous hash}, where the scope is the
 *   institution id or {@code platform}.</li>
 * </ul>
 * The leaf and node prefixes keep a row from ever passing for an inner node.
 */
public final class AuditHashing {

    /** The previous hash of a scope's first seal. */
    public static final String GENESIS = "0".repeat(64);
    private static final HexFormat HEX = HexFormat.of();
    private static final byte LEAF = 0x00;
    private static final byte NODE = 0x01;

    private AuditHashing() {
    }

    public static byte[] rowHash(AuditLogRow row) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        out.write(LEAF);
        field(out, row.id());
        field(out, row.tenantId());
        field(out, Long.toString(micros(row.occurredAt())));
        field(out, row.actorType());
        field(out, row.actorId());
        field(out, row.actorName());
        field(out, row.action());
        field(out, row.outcome());
        field(out, row.resourceType());
        field(out, row.resourceId());
        field(out, row.resourceReference());
        field(out, row.branchId());
        field(out, row.beforeState());
        field(out, row.afterState());
        field(out, row.metadata());
        field(out, row.ipAddress());
        field(out, row.userAgent());
        field(out, row.deviceId());
        field(out, row.correlationId());
        return sha256(out.toByteArray());
    }

    /**
     * @param leaves row hashes in {@code (occurred_at, id)} order
     * @return the root, in lower-case hex
     */
    public static String merkleRoot(List<byte[]> leaves) {
        if (leaves.isEmpty()) {
            return HEX.formatHex(sha256(new byte[0]));
        }
        List<byte[]> level = leaves;
        while (level.size() > 1) {
            List<byte[]> next = new ArrayList<>((level.size() + 1) / 2);
            for (int i = 0; i < level.size(); i += 2) {
                if (i + 1 == level.size()) {
                    next.add(level.get(i));
                } else {
                    byte[] pair = new byte[1 + 2 * 32];
                    pair[0] = NODE;
                    System.arraycopy(level.get(i), 0, pair, 1, 32);
                    System.arraycopy(level.get(i + 1), 0, pair, 33, 32);
                    next.add(sha256(pair));
                }
            }
            level = next;
        }
        return HEX.formatHex(level.getFirst());
    }

    /**
     * @param tenantId the institution, or {@code null} for the platform's own trail
     */
    @SuppressWarnings("java:S107")
    public static String sealHash(UUID tenantId, long sequenceNo, Instant rangeStart, Instant rangeEnd,
                                  int rowCount, String merkleRoot, String previousHash) {
        String header = String.join("|", "v1", tenantId == null ? "platform" : tenantId.toString(),
                Long.toString(sequenceNo), Long.toString(micros(rangeStart)), Long.toString(micros(rangeEnd)),
                Integer.toString(rowCount), merkleRoot, previousHash);
        return HEX.formatHex(sha256(header.getBytes(StandardCharsets.UTF_8)));
    }

    static long micros(Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant);
    }

    private static void field(ByteArrayOutputStream out, Object value) {
        if (value == null) {
            writeInt(out, -1);
            return;
        }
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        writeInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
