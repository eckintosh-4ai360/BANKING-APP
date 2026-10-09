package com.company.banking.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.audit.repository.AuditLogRepository;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditHashing;
import com.company.banking.audit.service.AuditSealService;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import com.company.banking.support.TestDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Audit seals (one-second periods in tests): the trail is sealed in one signed chain, nothing can be added to a
 * sealed period, changes made behind the application's back are found, and a seal waits for audit rows still
 * being written.
 */
class AuditSealIT extends IntegrationTest {

    @Autowired
    private AuditSealService sealService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLog;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void theTrailIsSealedPeriodByPeriodInOneSignedChain() throws Exception {
        TenantHandle tenant = fixtures.onboardTenant();
        fixtures.createBranch(tenant, "KUM");
        awaitNextPeriod();

        int written = seal(tenant.id());
        assertThat(written).isPositive();
        JsonNode seals = seals(tenant);
        assertThat(seals).hasSize(written);
        long sealedRows = 0;
        for (int i = 0; i < seals.size(); i++) {
            JsonNode seal = seals.get(i);
            assertThat(seal.get("sequenceNo").asLong()).as("newest first").isEqualTo(written - i);
            assertThat(seal.get("signature").asString()).matches("^[0-9a-f]{64}$");
            if (i + 1 < seals.size()) {
                assertThat(seal.get("rangeStart").asString()).isEqualTo(seals.get(i + 1).get("rangeEnd").asString());
                assertThat(seal.get("previousHash").asString())
                        .isEqualTo(seals.get(i + 1).get("sealHash").asString());
            }
            sealedRows += seal.get("rowCount").asInt();
        }
        assertThat(seals.get(written - 1).get("previousHash").asString()).isEqualTo(AuditHashing.GENESIS);
        Instant firstStart = Instant.parse(seals.get(written - 1).get("rangeStart").asString());
        Instant sealedThrough = Instant.parse(seals.get(0).get("rangeEnd").asString());
        assertThat(sealedRows).isPositive().isEqualTo(rowsBefore(tenant, sealedThrough));

        JsonNode report = verify(tenant, firstStart, sealedThrough);
        assertThat(report.get("intact").asBoolean()).isTrue();
        assertThat(report.get("sealsChecked").asInt()).isEqualTo(written);
        assertThat(report.get("rowsChecked").asLong()).isEqualTo(sealedRows);
        assertThat(Instant.parse(report.get("sealedThrough").asString())).isEqualTo(sealedThrough);
        assertThat(report.get("problems")).isEmpty();
    }

    @Test
    void nothingCanBeAddedToASealedPeriod() throws Exception {
        TenantHandle tenant = fixtures.onboardTenant();
        awaitNextPeriod();
        seal(tenant.id());
        Instant sealedThrough = Instant.parse(seals(tenant).get(0).get("rangeEnd").asString());

        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql("""
                        INSERT INTO core.audit_log (id, tenant_id, occurred_at, actor_type, action, outcome,
                                                    resource_type)
                        VALUES (:id, :tenantId, :at, 'SYSTEM', 'BACKDATED', 'SUCCESS', 'TEST')""")
                .param("id", UUID.randomUUID())
                .param("tenantId", tenant.id())
                .param("at", Timestamp.from(sealedThrough.minusMillis(1)))
                .update())).hasMessageContaining("The audit trail is sealed up to");

        fixtures.createBranch(tenant, "KUM");
        assertThat(rowsBefore(tenant, Instant.now().plusSeconds(60))).isGreaterThan(rowsBefore(tenant, sealedThrough));
    }

    @Test
    void changesMadeBehindTheApplicationsBackAreFound() throws Exception {
        TenantHandle tenant = fixtures.onboardTenant();
        awaitNextPeriod();
        fixtures.createBranch(tenant, "KUM");
        awaitNextPeriod();
        fixtures.createBranch(tenant, "TAM");
        awaitNextPeriod();
        seal(tenant.id());
        JsonNode seals = seals(tenant);
        Instant from = Instant.parse(seals.get(seals.size() - 1).get("rangeStart").asString());
        Instant to = Instant.parse(seals.get(0).get("rangeEnd").asString());
        List<UUID> rows = rowIds(tenant, to);
        UUID first = rows.getFirst();
        UUID last = rows.getLast();
        long firstSeal = sealOf(seals, first, tenant);
        long lastSeal = sealOf(seals, last, tenant);
        assertThat(firstSeal).isLessThan(lastSeal);

        // A superuser with triggers disabled rewrites one row and removes another.
        asSuperuser("UPDATE core.audit_log SET actor_name = 'Mallory' WHERE id = ?", first);
        asSuperuser("DELETE FROM core.audit_log WHERE id = ?", last);
        JsonNode report = verify(tenant, from, to);
        assertThat(report.get("intact").asBoolean()).isFalse();
        assertThat(problems(report)).containsExactlyInAnyOrder(
                firstSeal + " ROWS_CHANGED Sealed rows were altered",
                lastSeal + " ROWS_CHANGED " + rowCount(seals, lastSeal) + " rows were sealed, "
                        + (rowCount(seals, lastSeal) - 1) + " are there now");

        // Recomputing the seal over the rewritten rows needs the key: the signature and the next link give it away.
        JsonNode seal = bySequence(seals, firstSeal);
        Instant start = Instant.parse(seal.get("rangeStart").asString());
        Instant end = Instant.parse(seal.get("rangeEnd").asString());
        String forgedRoot = inTenant(tenant.id(), () -> {
            List<byte[]> leaves = new ArrayList<>();
            auditLog.forEachInRange(tenant.id(), start, end, row -> leaves.add(AuditHashing.rowHash(row)));
            return AuditHashing.merkleRoot(leaves);
        });
        String forgedHash = AuditHashing.sealHash(tenant.id(), firstSeal, start, end, seal.get("rowCount").asInt(),
                forgedRoot, seal.get("previousHash").asString());
        asSuperuser("UPDATE core.audit_seal SET merkle_root = ?, seal_hash = ? WHERE tenant_id = ? AND sequence_no = ?",
                forgedRoot, forgedHash, tenant.id(), firstSeal);
        assertThat(problems(verify(tenant, from, to))).containsExactlyInAnyOrder(
                firstSeal + " SIGNATURE_INVALID The signature does not match the seal hash",
                (firstSeal + 1) + " CHAIN_BROKEN Not linked to seal " + firstSeal,
                lastSeal + " ROWS_CHANGED " + rowCount(seals, lastSeal) + " rows were sealed, "
                        + (rowCount(seals, lastSeal) - 1) + " are there now");
        assertThat(inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT count(*) FROM core.audit_log WHERE action = 'AUDIT_TRAIL_VERIFIED'")
                .query(Long.class).single())).as("each check is audited").isEqualTo(2L);
    }

    @Test
    void aSealWaitsForAuditRowsStillBeingWritten() throws Exception {
        TenantHandle tenant = fixtures.onboardTenant();
        CountDownLatch recorded = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Object> slow = pool.submit(() -> inTenant(tenant.id(), () -> {
                auditService.record(AuditEvent.builder("SLOW_WORK", "TEST").build());
                recorded.countDown();
                pause(Duration.ofMillis(2_500));
                return null;
            }));
            recorded.await();
            awaitNextPeriod();

            long started = System.nanoTime();
            assertThat(seal(tenant.id())).isPositive();
            assertThat(Duration.ofNanos(System.nanoTime() - started)).as("waited for the open transaction")
                    .isGreaterThan(Duration.ofMillis(300));
            slow.get();
        } finally {
            pool.shutdownNow();
        }
        JsonNode seals = seals(tenant);
        Instant sealedThrough = Instant.parse(seals.get(0).get("rangeEnd").asString());
        assertThat(inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT count(*) FROM core.audit_log WHERE action = 'SLOW_WORK' AND occurred_at < :end")
                .param("end", Timestamp.from(sealedThrough))
                .query(Long.class).single())).as("the slow row is sealed").isEqualTo(1L);
        assertThat(verify(tenant, Instant.parse(seals.get(seals.size() - 1).get("rangeStart").asString()),
                sealedThrough).get("intact").asBoolean()).isTrue();
    }

    @Test
    void thePlatformTrailIsSealedOnItsOwn() throws Exception {
        String platformToken = fixtures.platformToken();
        awaitNextPeriod();
        assertThat(sealService.sealDue()).as("no institution bound").isPositive();

        JsonNode seals = api.get("/api/v1/platform/audit-seals", platformToken).expect(200).data().get("items");
        assertThat(seals).isNotEmpty();
        Instant sealedThrough = Instant.parse(seals.get(0).get("rangeEnd").asString());
        JsonNode report = api.get("/api/v1/platform/audit-seals/verification?from="
                + sealedThrough.minusSeconds(30) + "&to=" + sealedThrough, platformToken).expect(200).data();
        assertThat(report.get("intact").asBoolean()).isTrue();
        assertThat(report.get("sealsChecked").asInt()).isPositive();

        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        api.get("/api/v1/platform/audit-seals", tenant.adminToken()).expectError(403, "ACCESS_DENIED");
        api.get("/api/v1/audit-seals", teller.token()).expectError(403, "ACCESS_DENIED");
        api.get("/api/v1/audit-seals/verification?from=2027-01-01T00:00:00Z&to=2027-03-01T00:00:00Z",
                tenant.adminToken()).expectError(400, "VALIDATION_FAILED");
    }

    // ---------------------------------------------------------------------------------------------------------

    private int seal(UUID tenantId) {
        return TenantContext.callAs(tenantId, () -> sealService.sealDue());
    }

    private JsonNode seals(TenantHandle tenant) {
        return api.get("/api/v1/audit-seals?size=100", tenant.adminToken()).expect(200).data().get("items");
    }

    private JsonNode verify(TenantHandle tenant, Instant from, Instant to) {
        return api.get("/api/v1/audit-seals/verification?from=" + from + "&to=" + to, tenant.adminToken())
                .expect(200).data();
    }

    private static List<String> problems(JsonNode report) {
        List<String> problems = new ArrayList<>();
        report.get("problems").forEach(problem -> problems.add(problem.get("sequenceNo").asLong() + " "
                + problem.get("code").asString() + " " + problem.get("detail").asString()));
        return problems;
    }

    private long rowsBefore(TenantHandle tenant, Instant end) {
        return inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT count(*) FROM core.audit_log WHERE occurred_at < :end")
                .param("end", Timestamp.from(end))
                .query(Long.class).single());
    }

    private List<UUID> rowIds(TenantHandle tenant, Instant end) {
        return inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT id FROM core.audit_log WHERE occurred_at < :end ORDER BY occurred_at, id")
                .param("end", Timestamp.from(end))
                .query(UUID.class).list());
    }

    /** Sequence number of the seal covering an audit row. */
    private long sealOf(JsonNode seals, UUID rowId, TenantHandle tenant) {
        Instant at = inTenant(tenant.id(), () -> jdbcClient.sql("SELECT occurred_at FROM core.audit_log WHERE id = :id")
                .param("id", rowId)
                .query(Timestamp.class).single().toInstant());
        for (JsonNode seal : seals) {
            if (!at.isBefore(Instant.parse(seal.get("rangeStart").asString()))
                    && at.isBefore(Instant.parse(seal.get("rangeEnd").asString()))) {
                return seal.get("sequenceNo").asLong();
            }
        }
        throw new AssertionError("Row " + rowId + " is not sealed");
    }

    private static JsonNode bySequence(JsonNode seals, long sequenceNo) {
        for (JsonNode seal : seals) {
            if (seal.get("sequenceNo").asLong() == sequenceNo) {
                return seal;
            }
        }
        throw new AssertionError("No seal " + sequenceNo);
    }

    private static int rowCount(JsonNode seals, long sequenceNo) {
        return bySequence(seals, sequenceNo).get("rowCount").asInt();
    }

    private <T> T inTenant(UUID tenantId, Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return TenantContext.callAs(tenantId, () -> CurrentActor.callAsSystem(tenantId,
                () -> transaction.execute(status -> action.get())));
    }

    /** Runs a statement as the database superuser with triggers off, as an intruder with full access could. */
    private static void asSuperuser(String sql, Object... params) throws SQLException {
        TestDatabase.Server server = TestDatabase.get();
        try (Connection connection = DriverManager.getConnection(server.bankingUrl(), server.superUser(),
                server.superPassword())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET session_replication_role = replica");
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
        }
    }

    /** Waits until the current one-second period has ended, so what was just written can be sealed. */
    private static void awaitNextPeriod() {
        pause(Duration.ofMillis(1_000 - System.currentTimeMillis() % 1_000 + 50));
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
