package com.company.banking.common.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

/**
 * Runs a job on at most one application instance at a time, using a PostgreSQL session advisory lock (decision
 * D10). The lock lives on a dedicated connection held for the duration of the job.
 */
@Component
@RequiredArgsConstructor
public class ClusterLock {

    private final DataSource dataSource;

    /**
     * @return false when another instance holds the lock (the job was skipped)
     */
    public boolean runExclusively(String lockName, Runnable job) {
        try (Connection connection = dataSource.getConnection()) {
            if (!call(connection, "SELECT pg_try_advisory_lock(hashtextextended(?, 0))", lockName)) {
                return false;
            }
            try {
                job.run();
                return true;
            } finally {
                call(connection, "SELECT pg_advisory_unlock(hashtextextended(?, 0))", lockName);
            }
        } catch (SQLException failure) {
            throw new DataAccessResourceFailureException("Could not take cluster lock " + lockName, failure);
        }
    }

    private static boolean call(Connection connection, String sql, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lockName);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }
}
