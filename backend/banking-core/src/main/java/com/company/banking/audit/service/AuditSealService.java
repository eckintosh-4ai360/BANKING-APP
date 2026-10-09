package com.company.banking.audit.service;

import com.company.banking.audit.dto.AuditSealResponse;
import com.company.banking.audit.dto.AuditVerificationReport;
import com.company.banking.audit.dto.AuditVerificationReport.Problem;
import com.company.banking.audit.repository.AuditLogRepository;
import com.company.banking.audit.repository.AuditSealRepository;
import com.company.banking.audit.repository.AuditSealRow;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.tenant.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seals the audit trail period by period and verifies the seals against the trail. The trail is the bound
 * institution's, or the platform's own events when no institution is bound (row-level security agrees).
 *
 * <p>A period is sealed once it ended {@code lag} ago and under the trail's seal lock, which waits for audit rows
 * still being written; afterwards the database refuses rows dated inside it. See {@link AuditHashing} for the
 * hashes.
 */
@Service
public class AuditSealService {

    static final Duration MAX_VERIFICATION_RANGE = Duration.ofDays(31);
    private static final int LOCK_TIMEOUT_MS = 5_000;

    private final AuditSealRepository seals;
    private final AuditLogRepository auditLog;
    private final AuditSealKeys keys;
    private final AuditSealProperties properties;
    private final AuditService auditService;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public AuditSealService(AuditSealRepository seals, AuditLogRepository auditLog, AuditSealKeys keys,
                            AuditSealProperties properties, AuditService auditService,
                            PlatformTransactionManager transactionManager, Clock clock) {
        if (properties.period().toMillis() < 1_000 || properties.period().toMillis() % 1_000 != 0) {
            throw new IllegalStateException("banking.audit.seal.period must be a whole number of seconds");
        }
        if (properties.lag().isNegative() || properties.maxPerRun() < 1) {
            throw new IllegalStateException("banking.audit.seal.lag and max-per-run must not be negative or zero");
        }
        this.seals = seals;
        this.auditLog = auditLog;
        this.keys = keys;
        this.properties = properties;
        this.auditService = auditService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Seals the trail's due periods, oldest first, each in its own transaction (at most {@code max-per-run}).
     *
     * @return seals written
     */
    public int sealDue() {
        UUID tenantId = TenantContext.currentTenantId().orElse(null);
        int written = 0;
        while (written < properties.maxPerRun() && Boolean.TRUE.equals(transaction.execute(status ->
                sealNext(tenantId)))) {
            written++;
        }
        return written;
    }

    private boolean sealNext(UUID tenantId) {
        if (nextDue(tenantId).isEmpty()) {
            return false; // checked before taking the lock, which briefly holds up audit writes
        }
        seals.lockTrail(tenantId, LOCK_TIMEOUT_MS);
        Optional<AuditSealRow> last = seals.last(tenantId);
        Optional<Instant> due = nextDue(tenantId);
        if (due.isEmpty()) {
            return false;
        }
        Instant start = due.get();
        Instant end = start.plus(properties.period());
        List<byte[]> leaves = rowHashes(tenantId, start, end);
        long sequenceNo = last.map(seal -> seal.sequenceNo() + 1).orElse(1L);
        String previousHash = last.map(AuditSealRow::sealHash).orElse(AuditHashing.GENESIS);
        String merkleRoot = AuditHashing.merkleRoot(leaves);
        String sealHash = AuditHashing.sealHash(tenantId, sequenceNo, start, end, leaves.size(), merkleRoot,
                previousHash);
        seals.insert(new AuditSealRow(UuidV7.next(), tenantId, sequenceNo, start, end, leaves.size(), merkleRoot,
                previousHash, sealHash, keys.activeVersion(), keys.sign(sealHash), clock.instant()));
        return true;
    }

    /**
     * Start of the next period to seal, if one has ended at least {@code lag} ago: where the last seal ended, or
     * the period of the trail's first row.
     */
    private Optional<Instant> nextDue(UUID tenantId) {
        Instant cutoff = clock.instant().minus(properties.lag());
        return seals.last(tenantId).map(AuditSealRow::rangeEnd)
                .or(() -> auditLog.earliest(tenantId).map(this::periodStart))
                .filter(start -> !start.plus(properties.period()).isAfter(cutoff));
    }

    private Instant periodStart(Instant instant) {
        long period = properties.period().toMillis();
        return Instant.ofEpochMilli(Math.floorDiv(instant.toEpochMilli(), period) * period);
    }

    /**
     * Recomputes every seal overlapping {@code [from, to)} from the trail as it is now, and checks the chain into
     * the first of them. The check itself is audited.
     */
    @Transactional
    public AuditVerificationReport verify(Instant from, Instant to) {
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_VERIFICATION_RANGE) > 0) {
            throw new BankingException(CommonErrorCode.VALIDATION_FAILED,
                    "Verify a period of at most " + MAX_VERIFICATION_RANGE.toDays() + " days, with from before to.");
        }
        UUID tenantId = TenantContext.currentTenantId().orElse(null);
        List<AuditSealRow> range = seals.overlapping(tenantId, from, to);
        List<Problem> problems = new ArrayList<>();
        AuditSealRow previous = range.isEmpty() || range.getFirst().sequenceNo() == 1 ? null
                : seals.bySequence(tenantId, range.getFirst().sequenceNo() - 1).orElse(null);
        long rows = 0;
        for (AuditSealRow seal : range) {
            checkChain(seal, previous, problems);
            String recomputed = AuditHashing.sealHash(tenantId, seal.sequenceNo(), seal.rangeStart(),
                    seal.rangeEnd(), seal.rowCount(), seal.merkleRoot(), seal.previousHash());
            if (!recomputed.equals(seal.sealHash())) {
                problems.add(problem(seal, "SEAL_ALTERED", "The seal's fields no longer produce its hash"));
            }
            Optional<Boolean> signed = keys.verify(seal.keyVersion(), seal.sealHash(), seal.signature());
            if (signed.isEmpty()) {
                problems.add(problem(seal, "KEY_UNAVAILABLE", "Seal key v" + seal.keyVersion()
                        + " is not configured"));
            } else if (!signed.get()) {
                problems.add(problem(seal, "SIGNATURE_INVALID", "The signature does not match the seal hash"));
            }
            List<byte[]> leaves = rowHashes(tenantId, seal.rangeStart(), seal.rangeEnd());
            rows += leaves.size();
            if (leaves.size() != seal.rowCount()) {
                problems.add(problem(seal, "ROWS_CHANGED", seal.rowCount() + " rows were sealed, "
                        + leaves.size() + " are there now"));
            } else if (!AuditHashing.merkleRoot(leaves).equals(seal.merkleRoot())) {
                problems.add(problem(seal, "ROWS_CHANGED", "Sealed rows were altered"));
            }
            previous = seal;
        }
        AuditVerificationReport report = new AuditVerificationReport(from, to, range.size(), rows,
                seals.last(tenantId).map(AuditSealRow::rangeEnd).orElse(null), problems.isEmpty(),
                List.copyOf(problems));
        auditService.record(AuditEvent.builder("AUDIT_TRAIL_VERIFIED", "AUDIT_TRAIL")
                .metadata("from", from.toString())
                .metadata("to", to.toString())
                .metadata("sealsChecked", report.sealsChecked())
                .metadata("intact", report.intact())
                .metadata("problems", problems.size())
                .build());
        return report;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditSealResponse> list(PageRequest page) {
        UUID tenantId = TenantContext.currentTenantId().orElse(null);
        List<AuditSealResponse> items = seals.page(tenantId, (int) page.getOffset(), page.getPageSize()).stream()
                .map(AuditSealResponse::from)
                .toList();
        return PageResponse.of(items, page.getPageNumber(), page.getPageSize(), seals.count(tenantId));
    }

    private static void checkChain(AuditSealRow seal, AuditSealRow previous, List<Problem> problems) {
        if (seal.sequenceNo() == 1) {
            if (!AuditHashing.GENESIS.equals(seal.previousHash())) {
                problems.add(problem(seal, "CHAIN_BROKEN", "The first seal must not link to a previous one"));
            }
        } else if (previous == null) {
            problems.add(problem(seal, "CHAIN_BROKEN", "Seal " + (seal.sequenceNo() - 1) + " is missing"));
        } else if (previous.sequenceNo() != seal.sequenceNo() - 1 || !previous.rangeEnd().equals(seal.rangeStart())
                || !previous.sealHash().equals(seal.previousHash())) {
            problems.add(problem(seal, "CHAIN_BROKEN", "Not linked to seal " + previous.sequenceNo()));
        }
    }

    private List<byte[]> rowHashes(UUID tenantId, Instant from, Instant to) {
        List<byte[]> leaves = new ArrayList<>();
        auditLog.forEachInRange(tenantId, from, to, row -> leaves.add(AuditHashing.rowHash(row)));
        return leaves;
    }

    private static Problem problem(AuditSealRow seal, String code, String detail) {
        return new Problem(seal.sequenceNo(), seal.rangeStart(), seal.rangeEnd(), code, detail);
    }
}
