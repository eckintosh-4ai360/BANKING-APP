package com.company.banking.ledger.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.SqlParameterValue;

/**
 * Typed JDBC parameters, so nulls carry a SQL type and UUIDs bind as {@code uuid}.
 */
final class SqlParams {

    private SqlParams() {
    }

    static SqlParameterValue uuid(UUID value) {
        return new SqlParameterValue(Types.OTHER, value);
    }

    static SqlParameterValue text(String value) {
        return new SqlParameterValue(Types.VARCHAR, value);
    }

    static SqlParameterValue date(LocalDate value) {
        return new SqlParameterValue(Types.DATE, value == null ? null : Date.valueOf(value));
    }

    static SqlParameterValue decimal(BigDecimal value) {
        return new SqlParameterValue(Types.NUMERIC, value);
    }

    /**
     * An array literal for {@code CAST(:param AS uuid[])}; null means "no filter".
     */
    static SqlParameterValue uuidArray(Collection<UUID> values) {
        return text(values == null ? null
                : values.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}")));
    }
}
