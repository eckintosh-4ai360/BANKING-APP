package com.company.banking.fieldops.repository;

import java.sql.Types;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Collection numbers a device used but the server never received.
 */
@Repository
@RequiredArgsConstructor
public class SequenceGapRepository {

    /** Numbers {@code from} to {@code to}, both included, are missing. */
    public record Gap(long from, long to) {
    }

    private final JdbcClient jdbc;

    /**
     * The device's gaps below its highest received number, in order (from 1, since devices start counting at 1).
     */
    public List<Gap> gaps(UUID tenantId, UUID deviceId) {
        return jdbc.sql("""
                        SELECT previous + 1 AS gap_from, sequence_no - 1 AS gap_to
                        FROM (SELECT device_sequence_no AS sequence_no,
                                     coalesce(lag(device_sequence_no) OVER (ORDER BY device_sequence_no), 0)
                                         AS previous
                              FROM core.collection
                              WHERE tenant_id = :tenantId AND device_id = :deviceId) numbered
                        WHERE sequence_no > previous + 1
                        ORDER BY gap_from""")
                .param("tenantId", new SqlParameterValue(Types.OTHER, tenantId))
                .param("deviceId", new SqlParameterValue(Types.OTHER, deviceId))
                .query((rs, rowNum) -> new Gap(rs.getLong("gap_from"), rs.getLong("gap_to")))
                .list();
    }
}
