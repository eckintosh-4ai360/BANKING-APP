package com.company.banking.ledger.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.References;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.BalanceSnapshot;
import com.company.banking.ledger.dto.GlAccountRef;
import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.ledger.dto.PostedLine;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.PostingRequest;
import com.company.banking.ledger.dto.ReversalRequest;
import com.company.banking.ledger.entity.LedgerAccount;
import com.company.banking.ledger.exception.LedgerErrorCode;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.JournalSource;
import com.company.banking.ledger.model.NormalSide;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.ledger.repository.BalanceRepository;
import com.company.banking.ledger.repository.BalanceRepository.BalanceRow;
import com.company.banking.ledger.repository.JournalRepository;
import com.company.banking.ledger.repository.JournalRepository.JournalRow;
import com.company.banking.ledger.repository.JournalRepository.LineRow;
import com.company.banking.ledger.repository.JournalRepository.LineView;
import com.company.banking.ledger.repository.LedgerAccountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only writer of the ledger (decision D3).
 *
 * <p>Every posting runs inside the caller's transaction ({@code MANDATORY}), so the money movement commits or rolls
 * back together with whatever triggered it (the transaction record, the idempotency record). The engine:
 * <ol>
 *   <li>resolves and validates every line (postable GL, active sub-ledger account, currency precision);</li>
 *   <li>requires debits to equal credits per currency;</li>
 *   <li>adds inter-branch due-to/due-from lines so the journal also balances per branch (spec risk F2);</li>
 *   <li>locks the affected balances in a fixed order and checks available funds under the lock (spec risk F3);</li>
 *   <li>writes the journal; database triggers update the balances and re-check every invariant at commit.</li>
 * </ol>
 * The engine never rounds: callers pass amounts already in the currency's minor units.
 */
@Service
@RequiredArgsConstructor
public class PostingEngine {

    static final int MAX_LINES = 500;
    private static final int MAX_DESCRIPTION = 300;
    private static final int MAX_NARRATION = 200;
    private static final int MAX_REFERENCE = 60;
    private static final String RESOURCE = "JOURNAL";
    private static final String INTER_BRANCH_NARRATION = "Inter-branch settlement";

    private final ChartOfAccountService chartOfAccounts;
    private final LedgerAccountRepository ledgerAccounts;
    private final LedgerAccountService ledgerAccountService;
    private final CurrencyService currencies;
    private final AccountingPeriodService periods;
    private final BusinessDateService businessDates;
    private final JournalRepository journals;
    private final BalanceRepository balances;
    private final JdbcClient jdbcClient;
    private final AuditService auditService;

    /**
     * A fully resolved line. {@code ledgerSide} is the normal side of the sub-ledger account (null for GL lines).
     */
    private record Line(UUID chartOfAccountId, String glCode, UUID ledgerAccountId, NormalSide ledgerSide,
                        UUID branchId, String currency, EntryDirection direction, BigDecimal amount,
                        String narration) {

        BigDecimal signed() {
            return direction == EntryDirection.DEBIT ? amount : amount.negate();
        }
    }

    private record BranchCurrency(String currency, UUID branchId) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PostedJournal post(PostingRequest request) {
        return post(request, CurrentActor.currentActorId().orElse(null), request.approvedBy());
    }

    /**
     * Posts a journal approved through maker-checker: the maker is recorded as having posted it and the caller (the
     * checker) as having approved it. {@code request.approvedBy()} is ignored.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PostedJournal postApproved(PostingRequest request, UUID makerId) {
        return post(request, makerId, requireChecker());
    }

    private PostedJournal post(PostingRequest request, UUID postedBy, UUID approvedBy) {
        UUID tenantId = TenantContext.requireTenantId();
        requireValidHeader(request);
        LocalDate businessDate = businessDates.today();
        LocalDate valueDate = request.valueDate() == null ? businessDate : request.valueDate();
        if (valueDate.isAfter(businessDate)) {
            throw new BankingException(LedgerErrorCode.INVALID_POSTING, "The value date cannot be in the future.");
        }
        boolean manual = request.source() == JournalSource.MANUAL;
        if (manual && approvedBy == null) {
            throw new BankingException(LedgerErrorCode.APPROVAL_REQUIRED);
        }
        List<Line> lines = resolve(tenantId, request.lines(), manual);
        requireBalancedPerCurrency(lines);
        return write(tenantId, request.source(), request.sourceReference(), request.financialTransactionId(), null,
                request.originBranchId(), businessDate, valueDate, request.description().trim(), postedBy,
                approvedBy, withInterBranchLines(lines));
    }

    /**
     * Posts the exact mirror of a journal on the current business date. A journal is reversed at most once, and a
     * reversal is never itself reversed (post a new journal instead).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PostedJournal reverse(ReversalRequest request) {
        return reverse(request, CurrentActor.currentActorId().orElse(null), request.approvedBy());
    }

    /**
     * Reverses a journal after maker-checker: the maker is recorded as having posted the reversal and the caller
     * (the checker) as having approved it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PostedJournal reverseApproved(ReversalRequest request, UUID makerId) {
        return reverse(request, makerId, requireChecker());
    }

    private PostedJournal reverse(ReversalRequest request, UUID postedBy, UUID approvedBy) {
        UUID tenantId = TenantContext.requireTenantId();
        if (request.reason() == null || request.reason().isBlank() || request.reason().length() > 250) {
            throw new BankingException(LedgerErrorCode.INVALID_POSTING, "Give a reason of up to 250 characters.");
        }
        // Serializes concurrent reversals of the same journal (journals are immutable, so no row lock is possible).
        jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "journal-reversal:" + request.journalId())
                .query((rs, rowNum) -> Boolean.TRUE)
                .single();
        JournalRow original = journals.find(tenantId, request.journalId())
                .orElseThrow(() -> new ResourceNotFoundException("Journal"));
        if (JournalSource.REVERSAL.name().equals(original.sourceType())) {
            throw new BankingException(LedgerErrorCode.REVERSAL_NOT_ALLOWED);
        }
        if (journals.findReversalOf(tenantId, original.id()).isPresent()) {
            throw new BankingException(LedgerErrorCode.JOURNAL_ALREADY_REVERSED);
        }
        List<LineView> originalLines = journals.findLines(tenantId, original.id());
        Map<UUID, LedgerAccount> accounts = loadLedgerAccounts(tenantId, originalLines.stream()
                .map(LineView::ledgerAccountId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<Line> mirror = new ArrayList<>(originalLines.size());
        for (LineView line : originalLines) {
            GlAccountRef gl = chartOfAccounts.requirePostable(line.chartOfAccountId());
            NormalSide side = null;
            if (line.ledgerAccountId() != null) {
                LedgerAccount account = accounts.get(line.ledgerAccountId());
                if (account == null || !account.isActive()) {
                    throw new BankingException(LedgerErrorCode.LEDGER_ACCOUNT_CLOSED);
                }
                side = account.getNormalSide();
            }
            mirror.add(new Line(gl.id(), gl.code(), line.ledgerAccountId(), side, line.branchId(), line.currency(),
                    EntryDirection.fromCode(line.direction()).opposite(), line.amount(), line.narration()));
        }
        LocalDate businessDate = businessDates.today();
        PostedJournal reversal = write(tenantId, JournalSource.REVERSAL, original.sourceReference(),
                request.financialTransactionId(), original.id(), original.branchId(), businessDate, businessDate,
                "Reversal of " + original.journalNumber() + ": " + request.reason().trim(), postedBy, approvedBy,
                mirror);
        auditService.record(AuditEvent.builder("JOURNAL_REVERSED", RESOURCE)
                .resourceId(original.id())
                .resourceReference(original.journalNumber())
                .branchId(original.branchId())
                .metadata("reversalJournal", reversal.journalNumber())
                .metadata("reason", request.reason().trim())
                .build());
        return reversal;
    }

    private PostedJournal write(UUID tenantId, JournalSource source, String sourceReference, UUID transactionId,
                                UUID reverses, UUID originBranchId, LocalDate businessDate, LocalDate valueDate,
                                String description, UUID postedBy, UUID approvedBy, List<Line> lines) {
        if (approvedBy != null && approvedBy.equals(postedBy)) {
            throw new BankingException(CommonErrorCode.FOUR_EYES_VIOLATION);
        }
        periods.requireOpen(businessDate);
        requireFunds(tenantId, lines);

        UUID journalId = UuidV7.next();
        String number = References.next("JE", businessDate);
        journals.insertJournal(new JournalRow(journalId, tenantId, number, businessDate, valueDate, null,
                source.name(), sourceReference, transactionId, reverses, originBranchId, postedBy, approvedBy,
                description));
        // The database applies lines one by one and checks the overdraft rule after each. Writing every line that
        // raises a sub-ledger balance before any line that lowers one keeps each intermediate balance at or above
        // the final balance, which requireFunds has already verified.
        List<Line> ordered = new ArrayList<>(lines);
        ordered.sort(Comparator.comparingInt(line -> lowersSubLedgerBalance(line) ? 1 : 0));
        List<PostedLine> posted = new ArrayList<>(ordered.size());
        int lineNo = 0;
        for (Line line : ordered) {
            lineNo++;
            journals.insertLine(new LineRow(UuidV7.next(), tenantId, journalId, lineNo, line.chartOfAccountId(),
                    line.ledgerAccountId(), line.branchId(), line.currency(), line.direction().code(),
                    line.amount(), businessDate, line.narration()));
            posted.add(new PostedLine(lineNo, line.chartOfAccountId(), line.glCode(), line.ledgerAccountId(),
                    line.branchId(), line.currency(), line.direction(),
                    currencies.present(line.amount(), line.currency()), line.narration()));
        }
        Set<UUID> touched = lines.stream().map(Line::ledgerAccountId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, BalanceSnapshot> after = balances.find(tenantId, touched).stream()
                .map(ledgerAccountService::toSnapshot)
                .collect(Collectors.toMap(BalanceSnapshot::ledgerAccountId, Function.identity()));
        return new PostedJournal(journalId, number, businessDate, List.copyOf(posted), Map.copyOf(after));
    }

    /**
     * Locks every touched balance (ascending id order: no deadlocks between postings) and refuses a posting that
     * would take a balance-checked account below zero plus its overdraft limit. The database re-checks the same rule.
     */
    private void requireFunds(UUID tenantId, List<Line> lines) {
        Map<UUID, BigDecimal> deltas = new HashMap<>();
        for (Line line : lines) {
            if (line.ledgerAccountId() != null) {
                BigDecimal change = line.direction() == line.ledgerSide().increasingDirection()
                        ? line.amount() : line.amount().negate();
                deltas.merge(line.ledgerAccountId(), change, BigDecimal::add);
            }
        }
        for (BalanceRow row : balances.lockForUpdate(tenantId, deltas.keySet())) {
            BigDecimal delta = deltas.get(row.ledgerAccountId());
            if (row.balanceCheck() && delta.signum() < 0
                    && row.availableBalance().add(row.overdraftLimit()).add(delta).signum() < 0) {
                throw new BankingException(LedgerErrorCode.INSUFFICIENT_FUNDS);
            }
        }
    }

    private List<Line> resolve(UUID tenantId, List<PostingLine> requested, boolean manual) {
        Map<UUID, LedgerAccount> accounts = loadLedgerAccounts(tenantId, requested.stream()
                .map(PostingLine::ledgerAccountId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, GlAccountRef> glById = new HashMap<>();
        List<Line> lines = new ArrayList<>(requested.size());
        for (PostingLine line : requested) {
            if (line == null || line.direction() == null || targetCount(line) != 1) {
                throw new BankingException(LedgerErrorCode.INVALID_POSTING,
                        "Each line needs a direction and exactly one ledger account or GL account.");
            }
            if (line.narration() != null && line.narration().length() > MAX_NARRATION) {
                throw new BankingException(LedgerErrorCode.INVALID_POSTING, "Line narrations are limited to 200 characters.");
            }
            if (line.ledgerAccountId() != null) {
                lines.add(resolveLedgerLine(line, accounts.get(line.ledgerAccountId()), manual, glById));
            } else {
                lines.add(resolveGlLine(line, manual, glById));
            }
        }
        return lines;
    }

    private Line resolveLedgerLine(PostingLine line, LedgerAccount account, boolean manual,
                                   Map<UUID, GlAccountRef> glById) {
        if (account == null) {
            throw new BankingException(LedgerErrorCode.INVALID_POSTING, "Unknown ledger account.");
        }
        if (!account.isActive()) {
            throw new BankingException(LedgerErrorCode.LEDGER_ACCOUNT_CLOSED);
        }
        if (manual) {
            throw new BankingException(LedgerErrorCode.GL_ACCOUNT_NOT_MANUAL,
                    "Manual journals cannot post to sub-ledger accounts; use an adjustment transaction.");
        }
        if ((line.branchId() != null && !line.branchId().equals(account.getBranchId()))
                || (line.currency() != null && !line.currency().equals(account.getCurrency()))) {
            throw new BankingException(LedgerErrorCode.INVALID_POSTING,
                    "The line's branch or currency differs from its ledger account.");
        }
        GlAccountRef gl = glById.computeIfAbsent(account.getChartOfAccountId(), chartOfAccounts::requirePostable);
        currencies.requireValidAmount(line.amount(), account.getCurrency());
        return new Line(gl.id(), gl.code(), account.getId(), account.getNormalSide(), account.getBranchId(),
                account.getCurrency(), line.direction(), line.amount(), line.narration());
    }

    private Line resolveGlLine(PostingLine line, boolean manual, Map<UUID, GlAccountRef> glById) {
        GlAccountRef gl = line.chartOfAccountId() != null
                ? glById.computeIfAbsent(line.chartOfAccountId(), chartOfAccounts::requirePostable)
                : chartOfAccounts.requireSystem(line.systemAccount());
        if (manual && !gl.manualPostingAllowed()) {
            throw new BankingException(LedgerErrorCode.GL_ACCOUNT_NOT_MANUAL);
        }
        if (line.branchId() == null || line.currency() == null) {
            throw new BankingException(LedgerErrorCode.INVALID_POSTING, "GL lines need a branch and a currency.");
        }
        currencies.requireValidAmount(line.amount(), line.currency());
        return new Line(gl.id(), gl.code(), null, null, line.branchId(), line.currency(), line.direction(),
                line.amount(), line.narration());
    }

    private static void requireBalancedPerCurrency(List<Line> lines) {
        Map<String, BigDecimal> net = new HashMap<>();
        lines.forEach(line -> net.merge(line.currency(), line.signed(), BigDecimal::add));
        if (net.values().stream().anyMatch(total -> total.signum() != 0)) {
            throw new BankingException(LedgerErrorCode.UNBALANCED_JOURNAL);
        }
    }

    /**
     * Makes a journal balance per branch as well as per currency (spec risk F2). A branch that received more
     * debits than credits owes the others ("due to"); one with more credits is owed ("due from"). Across the
     * institution the due-to and due-from balances always net to zero.
     */
    private List<Line> withInterBranchLines(List<Line> lines) {
        Map<BranchCurrency, BigDecimal> net = new LinkedHashMap<>();
        lines.forEach(line -> net.merge(new BranchCurrency(line.currency(), line.branchId()), line.signed(),
                BigDecimal::add));
        if (net.values().stream().allMatch(total -> total.signum() == 0)) {
            return lines;
        }
        GlAccountRef dueTo = chartOfAccounts.requireSystem(SystemAccount.INTER_BRANCH_DUE_TO);
        GlAccountRef dueFrom = chartOfAccounts.requireSystem(SystemAccount.INTER_BRANCH_DUE_FROM);
        List<Line> balanced = new ArrayList<>(lines);
        net.forEach((key, total) -> {
            if (total.signum() > 0) {
                balanced.add(new Line(dueTo.id(), dueTo.code(), null, null, key.branchId(), key.currency(),
                        EntryDirection.CREDIT, total, INTER_BRANCH_NARRATION));
            } else if (total.signum() < 0) {
                balanced.add(new Line(dueFrom.id(), dueFrom.code(), null, null, key.branchId(), key.currency(),
                        EntryDirection.DEBIT, total.negate(), INTER_BRANCH_NARRATION));
            }
        });
        return balanced;
    }

    private Map<UUID, LedgerAccount> loadLedgerAccounts(UUID tenantId, Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return ledgerAccounts.findAllByTenantIdAndIdIn(tenantId, new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(LedgerAccount::getId, Function.identity()));
    }

    private static boolean lowersSubLedgerBalance(Line line) {
        return line.ledgerAccountId() != null && line.direction() != line.ledgerSide().increasingDirection();
    }

    private static int targetCount(PostingLine line) {
        int count = 0;
        if (line.ledgerAccountId() != null) {
            count++;
        }
        if (line.chartOfAccountId() != null) {
            count++;
        }
        if (line.systemAccount() != null) {
            count++;
        }
        return count;
    }

    /**
     * The person approving a maker-checker action: the current actor, who must be a person.
     */
    private static UUID requireChecker() {
        return CurrentActor.currentActorId()
                .orElseThrow(() -> new BankingException(LedgerErrorCode.APPROVAL_REQUIRED));
    }

    private static void requireValidHeader(PostingRequest request) {
        if (request.source() == null || request.source() == JournalSource.REVERSAL) {
            throw new BankingException(LedgerErrorCode.INVALID_POSTING,
                    "A journal needs a source; reversals go through reverse().");
        }
        if (request.description() == null || request.description().isBlank()
                || request.description().length() > MAX_DESCRIPTION) {
            throw new BankingException(LedgerErrorCode.INVALID_POSTING, "A description of up to 300 characters is required.");
        }
        if (request.sourceReference() != null && request.sourceReference().length() > MAX_REFERENCE) {
            throw new BankingException(LedgerErrorCode.INVALID_POSTING, "The source reference is too long.");
        }
        if (request.originBranchId() == null) {
            throw new BankingException(LedgerErrorCode.INVALID_POSTING, "The originating branch is required.");
        }
        if (request.lines() == null || request.lines().size() < 2 || request.lines().size() > MAX_LINES) {
            throw new BankingException(LedgerErrorCode.INVALID_POSTING, "A journal needs between 2 and 500 lines.");
        }
    }
}
