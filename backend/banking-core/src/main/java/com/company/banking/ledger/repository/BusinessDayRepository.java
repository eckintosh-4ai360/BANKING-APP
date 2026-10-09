package com.company.banking.ledger.repository;

import static com.company.banking.ledger.repository.SqlParams.date;
import static com.company.banking.ledger.repository.SqlParams.uuid;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The institution's current business date (one row per tenant, moved forward only by end-of-day).
 */
@Repository
@RequiredArgsConstructor
public class BusinessDayRepository {

    public record BusinessDay(LocalDate businessDate, LocalDate previousBusinessDate) {
    }

    private final JdbcClient jdbc;

    public Optional<BusinessDay> find(UUID tenantId) {
        return jdbc.sql("SELECT business_date, previous_business_date FROM core.business_day WHERE tenant_id = :tenantId")
                .param("tenantId", uuid(tenantId))
                .query((rs, rowNum) -> new BusinessDay(rs.getObject("business_date", LocalDate.class),
                        rs.getObject("previous_business_date", LocalDate.class)))
                .optional();
    }

    /**
     * Row lock: serialises the date roll with anything that must see a stable business date.
     */
    public Optional<BusinessDay> lock(UUID tenantId) {
        return jdbc.sql("SELECT business_date, previous_business_date FROM core.business_day WHERE tenant_id = :tenantId"
                        + " FOR UPDATE")
                .param("tenantId", uuid(tenantId))
                .query((rs, rowNum) -> new BusinessDay(rs.getObject("business_date", LocalDate.class),
                        rs.getObject("previous_business_date", LocalDate.class)))
                .optional();
    }

    /**
     * Shared row lock for postings: many postings hold it together, and the end-of-day roll (which needs the
     * exclusive lock) waits until they commit.
     */
    public Optional<BusinessDay> lockShared(UUID tenantId) {
        return jdbc.sql("SELECT business_date, previous_business_date FROM core.business_day WHERE tenant_id = :tenantId"
                        + " FOR SHARE")
                .param("tenantId", uuid(tenantId))
                .query((rs, rowNum) -> new BusinessDay(rs.getObject("business_date", LocalDate.class),
                        rs.getObject("previous_business_date", LocalDate.class)))
                .optional();
    }

    public void insertIfAbsent(UUID tenantId, LocalDate businessDate, Instant now) {
        jdbc.sql("INSERT INTO core.business_day (tenant_id, business_date, updated_at) VALUES (:tenantId, :date, :now)"
                        + " ON CONFLICT (tenant_id) DO NOTHING")
                .param("tenantId", uuid(tenantId))
                .param("date", date(businessDate))
                .param("now", Timestamp.from(now))
                .update();
    }

    public void advance(UUID tenantId, LocalDate next, LocalDate previous, Instant now, UUID by) {
        jdbc.sql("UPDATE core.business_day SET business_date = :next, previous_business_date = :previous,"
                        + " updated_at = :now, updated_by = :by, version = version + 1 WHERE tenant_id = :tenantId")
                .param("tenantId", uuid(tenantId))
                .param("next", date(next))
                .param("previous", date(previous))
                .param("now", Timestamp.from(now))
                .param("by", uuid(by))
                .update();
    }
}
