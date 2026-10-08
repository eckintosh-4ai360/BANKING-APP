package com.company.banking.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.support.Fixtures.TenantHandle;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The ledger invariants hold even for code that bypasses the Posting Engine: these tests write SQL directly as the
 * restricted application role.
 */
class LedgerDatabaseInvariantsIT extends LedgerIntegrationTest {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final String CHECK_VIOLATION = "23514";
    private static final String UNIQUE_VIOLATION = "23505";

    @Autowired
    private DataSource dataSource;

    private TenantHandle tenant;
    private UUID headOffice;
    private UUID otherBranch;
    private UUID cashGl;
    private UUID depositsGl;
    private UUID depositsHeader;
    private UUID account;
    private LocalDate today;

    @BeforeEach
    void setUp() throws SQLException {
        tenant = fixtures.onboardTenant();
        headOffice = tenant.headOfficeId();
        otherBranch = fixtures.createBranch(tenant, "B2");
        account = openDepositAccount(tenant.id(), headOffice, "GHS", "Customer");
        deposit(tenant.id(), account, headOffice, "GHS", "100.00");
        // Read inside a transaction: the tenant setting must stay transaction-local, never leak into the pool.
        committing(connection -> {
            cashGl = glId(connection, "1110");
            depositsGl = glId(connection, "2110");
            depositsHeader = glId(connection, "2100");
            today = queryDate(connection, "SELECT max(business_date) FROM core.journal_entry");
        });
    }

    @Test
    void journalsAndEntriesCannotBeChangedOrDeleted() {
        assertSqlState(INSUFFICIENT_PRIVILEGE, () -> committing(connection ->
                execute(connection, "UPDATE core.ledger_entry SET amount = amount + 1")));
        assertSqlState(INSUFFICIENT_PRIVILEGE, () -> committing(connection ->
                execute(connection, "DELETE FROM core.ledger_entry")));
        assertSqlState(INSUFFICIENT_PRIVILEGE, () -> committing(connection ->
                execute(connection, "UPDATE core.journal_entry SET description = 'tampered'")));
        assertSqlState(INSUFFICIENT_PRIVILEGE, () -> committing(connection ->
                execute(connection, "TRUNCATE core.journal_entry CASCADE")));
    }

    @Test
    void balancesCanOnlyChangeThroughTheLedger() {
        assertSqlState(INSUFFICIENT_PRIVILEGE, () -> committing(connection ->
                execute(connection, "UPDATE core.account_balance SET ledger_balance = 1000000")));
        assertSqlState(INSUFFICIENT_PRIVILEGE, () -> committing(connection ->
                execute(connection, "UPDATE core.account_balance SET hold_amount = 0")));
        assertSqlState(INSUFFICIENT_PRIVILEGE, () -> committing(connection ->
                execute(connection, "DELETE FROM core.account_balance")));
    }

    @Test
    void anUnbalancedJournalIsRejectedAtCommit() {
        assertSqlState(CHECK_VIOLATION, () -> committing(connection -> {
            UUID journal = insertJournal(connection, today, null);
            insertLine(connection, journal, 1, cashGl, null, headOffice, "D", "10.00", today);
            insertLine(connection, journal, 2, depositsGl, account, headOffice, "C", "9.00", today);
        }));
    }

    @Test
    void aJournalNeedsAtLeastTwoLines() {
        assertSqlState(CHECK_VIOLATION, () -> committing(connection -> {
            UUID journal = insertJournal(connection, today, null);
            insertLine(connection, journal, 1, cashGl, null, headOffice, "D", "10.00", today);
        }));
        assertSqlState(CHECK_VIOLATION, () -> committing(connection -> insertJournal(connection, today, null)));
    }

    @Test
    void everyBranchMustBalanceOnItsOwn() {
        assertSqlState(CHECK_VIOLATION, () -> committing(connection -> {
            UUID journal = insertJournal(connection, today, null);
            insertLine(connection, journal, 1, cashGl, null, headOffice, "D", "10.00", today);
            insertLine(connection, journal, 2, cashGl, null, otherBranch, "C", "10.00", today);
        }));
    }

    @Test
    void linesCannotBeAddedToAJournalAfterItsTransaction() throws SQLException {
        UUID[] journal = new UUID[1];
        committing(connection -> {
            journal[0] = insertJournal(connection, today, null);
            insertLine(connection, journal[0], 1, cashGl, null, headOffice, "D", "5.00", today);
            insertLine(connection, journal[0], 2, depositsGl, account, headOffice, "C", "5.00", today);
        });
        assertSqlState(CHECK_VIOLATION, () -> committing(connection -> {
            insertLine(connection, journal[0], 3, cashGl, null, headOffice, "D", "1.00", today);
            insertLine(connection, journal[0], 4, depositsGl, account, headOffice, "C", "1.00", today);
        }));
    }

    @Test
    void headerAccountsAndMismatchedSubLedgerLinesAreRejected() {
        assertSqlState(CHECK_VIOLATION, () -> committing(connection -> {
            UUID journal = insertJournal(connection, today, null);
            insertLine(connection, journal, 1, cashGl, null, headOffice, "D", "1.00", today);
            insertLine(connection, journal, 2, depositsHeader, null, headOffice, "C", "1.00", today);
        }));
        assertSqlState(CHECK_VIOLATION, () -> committing(connection -> {
            UUID journal = insertJournal(connection, today, null);
            insertLine(connection, journal, 1, cashGl, null, otherBranch, "D", "1.00", today);
            insertLine(connection, journal, 2, depositsGl, account, otherBranch, "C", "1.00", today);
        }));
    }

    @Test
    void theDatabaseItselfRefusesAnOverdraft() {
        assertSqlState(CHECK_VIOLATION, () -> committing(connection -> {
            UUID journal = insertJournal(connection, today, null);
            insertLine(connection, journal, 1, depositsGl, account, headOffice, "D", "100.01", today);
            insertLine(connection, journal, 2, cashGl, null, headOffice, "C", "100.01", today);
        }));
        assertThat(balanceOf(tenant.id(), account)).isEqualByComparingTo("100.00");
    }

    @Test
    void aReversalMustMirrorItsJournalAndHappensOnce() throws SQLException {
        PostedJournal original = deposit(tenant.id(), account, headOffice, "GHS", "20.00");
        assertSqlState(CHECK_VIOLATION, () -> committing(connection -> {
            UUID reversal = insertJournal(connection, today, original.id());
            insertLine(connection, reversal, 1, depositsGl, account, headOffice, "D", "19.00", today);
            insertLine(connection, reversal, 2, cashGl, null, headOffice, "C", "19.00", today);
        }));
        committing(connection -> {
            UUID reversal = insertJournal(connection, today, original.id());
            insertLine(connection, reversal, 1, depositsGl, account, headOffice, "D", "20.00", today);
            insertLine(connection, reversal, 2, cashGl, null, headOffice, "C", "20.00", today);
        });
        assertSqlState(UNIQUE_VIOLATION, () -> committing(connection -> insertJournal(connection, today, original.id())));
    }

    @Test
    void nothingPostsIntoAClosedPeriod() throws SQLException {
        LocalDate lastMonth = today.minusMonths(1).withDayOfMonth(1);
        inTenant(tenant.id(), () -> {
            periods.requireOpen(lastMonth);
            return periods.close(lastMonth);
        });
        assertSqlState(CHECK_VIOLATION, () -> committing(connection -> {
            UUID journal = insertJournal(connection, lastMonth.plusDays(1), null);
            insertLine(connection, journal, 1, cashGl, null, headOffice, "D", "1.00", lastMonth.plusDays(1));
            insertLine(connection, journal, 2, depositsGl, account, headOffice, "C", "1.00", lastMonth.plusDays(1));
        }));
        assertSqlState(CHECK_VIOLATION, () -> committing(connection -> execute(connection,
                "UPDATE core.accounting_period SET status = 'OPEN', closed_at = NULL WHERE period_start = '"
                        + lastMonth + "'")));
    }

    // ------------------------------------------------------------------------------------------- helpers

    private void committing(SqlWork work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenant(connection);
                work.run(connection);
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void setTenant(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            statement.setString(1, tenant.id().toString());
            statement.execute();
        }
    }

    private UUID insertJournal(Connection connection, LocalDate businessDate, UUID reverses) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO core.journal_entry (id, tenant_id, journal_number, business_date, value_date, source_type,
                    reverses_journal_id, branch_id, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Direct SQL')""")) {
            insert.setObject(1, id);
            insert.setObject(2, tenant.id());
            insert.setString(3, "SQL-" + id.toString().substring(0, 12));
            insert.setDate(4, Date.valueOf(businessDate));
            insert.setDate(5, Date.valueOf(businessDate));
            insert.setString(6, reverses == null ? "MANUAL" : "REVERSAL");
            insert.setObject(7, reverses);
            insert.setObject(8, headOffice);
            insert.executeUpdate();
        }
        return id;
    }

    @SuppressWarnings("java:S107")
    private void insertLine(Connection connection, UUID journal, int lineNo, UUID gl, UUID ledgerAccount, UUID branch,
                            String direction, String amount, LocalDate businessDate) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO core.ledger_entry (id, tenant_id, journal_entry_id, line_no, chart_of_account_id,
                    ledger_account_id, branch_id, currency, direction, amount, business_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'GHS', ?, ?, ?)""")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, tenant.id());
            insert.setObject(3, journal);
            insert.setInt(4, lineNo);
            insert.setObject(5, gl);
            insert.setObject(6, ledgerAccount);
            insert.setObject(7, branch);
            insert.setString(8, direction);
            insert.setBigDecimal(9, money(amount));
            insert.setDate(10, Date.valueOf(businessDate));
            insert.executeUpdate();
        }
    }

    private static UUID glId(Connection connection, String code) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT id FROM core.chart_of_account WHERE code = ?")) {
            query.setString(1, code);
            try (ResultSet rs = query.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static LocalDate queryDate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1, LocalDate.class);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void assertSqlState(String state, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo(state);
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection connection) throws SQLException;
    }
}
