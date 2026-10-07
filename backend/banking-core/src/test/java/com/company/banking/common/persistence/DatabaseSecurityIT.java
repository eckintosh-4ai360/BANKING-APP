package com.company.banking.common.persistence;

import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the database itself enforces tenant isolation and immutability for the application's runtime role,
 * independently of any application code.
 */
class DatabaseSecurityIT extends IntegrationTest {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CHECK_VIOLATION = "23514";

    @Autowired
    private DataSource dataSource;

    @Test
    void runtimeRoleIsNeitherSuperuserNorAbleToBypassRowLevelSecurity() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT current_user, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("banking_app");
            assertThat(rs.getBoolean(2)).isFalse();
            assertThat(rs.getBoolean(3)).isFalse();
        }
    }

    @Test
    void tenantRowsAreInvisibleWithoutTenantContext() throws SQLException {
        fixtures.onboardTenant();
        inTransaction(null, connection -> {
            assertThat(count(connection, "SELECT count(*) FROM core.branch")).isZero();
            assertThat(count(connection, "SELECT count(*) FROM core.staff")).isZero();
            assertThat(count(connection, "SELECT count(*) FROM core.role")).isZero();
            assertThat(count(connection, "SELECT count(*) FROM core.staff_credential")).isZero();
            // the registry itself is visible in platform context
            assertThat(count(connection, "SELECT count(*) FROM core.tenant")).isPositive();
        });
    }

    @Test
    void tenantContextSeesOnlyItsOwnRows() throws SQLException {
        TenantHandle a = fixtures.onboardTenant();
        TenantHandle b = fixtures.onboardTenant();
        inTransaction(a.id(), connection -> {
            assertThat(count(connection, "SELECT count(*) FROM core.branch WHERE tenant_id = '" + b.id() + "'"))
                    .isZero();
            assertThat(count(connection, "SELECT count(*) FROM core.branch")).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM core.tenant")).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM core.platform_user")).isZero();
            assertThat(count(connection, "SELECT count(*) FROM core.audit_log WHERE tenant_id IS NULL")).isZero();
        });
    }

    @Test
    void insertingIntoAnotherTenantIsRejectedByRowLevelSecurity() {
        TenantHandle a = fixtures.onboardTenant();
        TenantHandle b = fixtures.onboardTenant();
        assertThatThrownBy(() -> inTransaction(a.id(), connection -> {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO core.branch (id, tenant_id, code, name, branch_type, status, created_at, updated_at)
                    VALUES (?, ?, 'X1', 'Injected', 'BRANCH', 'ACTIVE', now(), now())""")) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, b.id());
                insert.executeUpdate();
            }
        })).isInstanceOf(SQLException.class)
                .extracting(ex -> ((SQLException) ex).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void crossTenantReferencesAreRejectedByCompositeForeignKeys() {
        TenantHandle a = fixtures.onboardTenant();
        TenantHandle b = fixtures.onboardTenant();
        assertThatThrownBy(() -> inTransaction(a.id(), connection -> {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO core.staff (id, tenant_id, employee_number, first_name, last_name, email,
                        home_branch_id, all_branches_access, status, created_at, updated_at)
                    VALUES (?, ?, 'X-1', 'Cross', 'Tenant', 'x@example.test', ?, false, 'ACTIVE', now(), now())""")) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, a.id());
                insert.setObject(3, b.headOfficeId());
                insert.executeUpdate();
            }
        })).isInstanceOf(SQLException.class)
                .extracting(ex -> ((SQLException) ex).getSQLState()).isEqualTo(FOREIGN_KEY_VIOLATION);
    }

    @Test
    void auditLogIsAppendOnlyForTheApplication() {
        TenantHandle a = fixtures.onboardTenant();
        assertThatThrownBy(() -> inTransaction(a.id(), connection ->
                execute(connection, "UPDATE core.audit_log SET action = 'TAMPERED'")))
                .isInstanceOf(SQLException.class)
                .extracting(ex -> ((SQLException) ex).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        assertThatThrownBy(() -> inTransaction(a.id(), connection ->
                execute(connection, "DELETE FROM core.audit_log")))
                .isInstanceOf(SQLException.class)
                .extracting(ex -> ((SQLException) ex).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void searchFunctionEnforcesTheTenantItself() throws SQLException {
        TenantHandle a = fixtures.onboardTenant();
        TenantHandle b = fixtures.onboardTenant();
        var officerA = fixtures.createStaff(a, "officer", a.headOfficeId(), true, "LOAN_OFFICER");
        fixtures.createIndividual(officerA.token(), a.headOfficeId(), "Ama", "Searchable", "+233244111222");
        String call = "SELECT count(*) FROM core.search_customers('%searchable%', null, null, null, null, null, "
                + "null, null, null, 50, 0)";

        inTransaction(a.id(), connection -> assertThat(count(connection, call)).isEqualTo(1));
        inTransaction(b.id(), connection -> assertThat(count(connection, call)).isZero());
        inTransaction(null, connection -> assertThat(count(connection, call)).isZero());
    }

    @Test
    void platformPermissionsCannotBeGrantedToInstitutionRolesEvenWithDirectSql() {
        TenantHandle a = fixtures.onboardTenant();
        UUID roleId = a.roles().get("TELLER");
        assertThatThrownBy(() -> inTransaction(a.id(), connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO core.role_permission (role_id, tenant_id, permission_code) VALUES (?, ?, ?)")) {
                insert.setObject(1, roleId);
                insert.setObject(2, a.id());
                insert.setString(3, "platform.tenant.manage");
                insert.executeUpdate();
            }
        })).isInstanceOf(SQLException.class)
                .extracting(ex -> ((SQLException) ex).getSQLState()).isEqualTo(CHECK_VIOLATION);
    }

    private void inTransaction(UUID tenantId, SqlWork work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement setTenant = connection.prepareStatement(
                        "SELECT set_config('app.tenant_id', ?, true)")) {
                    setTenant.setString(1, tenantId == null ? "" : tenantId.toString());
                    setTenant.execute();
                }
                work.run(connection);
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection connection) throws SQLException;
    }
}
