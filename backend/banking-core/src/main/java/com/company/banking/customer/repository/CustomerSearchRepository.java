package com.company.banking.customer.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calls {@code core.search_customers} (see V12): index-assisted search that enforces the tenant itself, because
 * row-level security prevents PostgreSQL from using trigram indexes for LIKE. Returns ids only; rows are loaded
 * through the normal, RLS-protected repository.
 */
@Repository
@RequiredArgsConstructor
public class CustomerSearchRepository {

    private final JdbcClient jdbcClient;

    public Result search(Query query) {
        List<Row> rows = jdbcClient.sql("""
                        SELECT customer_id, total_count
                        FROM core.search_customers(:namePattern, :exact, :phonePattern, :email,
                            CAST(:branchIds AS uuid[]), :branchId, :status, :kycStatus, :customerType, :limit, :offset)
                        """)
                .param("namePattern", text(query.namePattern()))
                .param("exact", text(query.exact()))
                .param("phonePattern", text(query.phonePattern()))
                .param("email", text(query.email()))
                .param("branchIds", text(query.branchIds() == null ? null : query.branchIds().stream()
                        .map(UUID::toString).collect(Collectors.joining(",", "{", "}"))))
                .param("branchId", new SqlParameterValue(Types.OTHER, query.branchId()))
                .param("status", text(query.status()))
                .param("kycStatus", text(query.kycStatus()))
                .param("customerType", text(query.customerType()))
                .param("limit", query.limit())
                .param("offset", query.offset())
                .query((rs, rowNum) -> new Row(rs.getObject("customer_id", UUID.class), rs.getLong("total_count")))
                .list();
        return new Result(rows.stream().map(Row::customerId).toList(), rows.isEmpty() ? 0 : rows.getFirst().total());
    }

    private static SqlParameterValue text(String value) {
        return new SqlParameterValue(Types.VARCHAR, value);
    }

    /**
     * @param namePattern lower-case LIKE pattern with wildcards already escaped; {@code null} for no text search
     * @param branchIds   branches the caller may see; {@code null} for all branches of the institution
     */
    public record Query(String namePattern, String exact, String phonePattern, String email, Set<UUID> branchIds,
                        UUID branchId, String status, String kycStatus, String customerType, int limit,
                        int offset) {
    }

    public record Result(List<UUID> ids, long total) {
    }

    private record Row(UUID customerId, long total) {
    }
}
