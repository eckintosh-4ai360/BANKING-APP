package com.company.banking.teller.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.References;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.security.Permissions;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.PostingRequest;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.JournalSource;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.ledger.service.PostingEngine;
import com.company.banking.teller.dto.CashCountRequest;
import com.company.banking.teller.dto.CashDrawerRef;
import com.company.banking.teller.dto.CloseSessionRequest;
import com.company.banking.teller.dto.OpenSessionRequest;
import com.company.banking.teller.dto.SessionResponse;
import com.company.banking.teller.dto.SupervisorDecisionRequest;
import com.company.banking.teller.entity.CashCount;
import com.company.banking.teller.entity.CashDrawer;
import com.company.banking.teller.entity.TellerSession;
import com.company.banking.teller.exception.TellerErrorCode;
import com.company.banking.teller.repository.CashCountRepository;
import com.company.banking.teller.repository.TellerSessionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Teller sessions. A teller opens a session on a drawer, takes and pays out cash only through it, and closes it with
 * a blind count. The expected cash is always the drawer's ledger balance. A difference is posted (shortage to the
 * teller shortages account, overage to other income) only when a supervisor accepts it.
 */
@Service
@RequiredArgsConstructor
public class TellerSessionService {

    static final String RESOURCE = "TELLER_SESSION";
    private static final List<TellerSession.Status> ACTIVE = List.of(TellerSession.Status.OPEN,
            TellerSession.Status.BALANCING);

    private final TellerSessionRepository sessions;
    private final CashCountRepository counts;
    private final CashPointService cashPoints;
    private final LedgerAccountService ledgerAccounts;
    private final CurrencyService currencies;
    private final PostingEngine postingEngine;
    private final BusinessDateService businessDates;
    private final AuditService auditService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional
    public SessionResponse open(OpenSessionRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID tellerId = CurrentActor.require().id();
        CashDrawer drawer = cashPoints.drawerInScope(request.drawerId());
        if (drawer.getStatus() != CashDrawer.Status.ACTIVE) {
            throw new BankingException(TellerErrorCode.DRAWER_NOT_AVAILABLE);
        }
        if (sessions.findFirstByTenantIdAndTellerIdAndStatusIn(tenantId, tellerId, ACTIVE).isPresent()) {
            throw new BankingException(TellerErrorCode.SESSION_ALREADY_OPEN);
        }
        if (sessions.findFirstByTenantIdAndCashDrawerIdAndStatusIn(tenantId, drawer.getId(), ACTIVE).isPresent()) {
            throw new BankingException(TellerErrorCode.DRAWER_IN_USE);
        }
        BigDecimal balance = ledgerAccounts.lockBalance(drawer.getLedgerAccountId()).ledgerBalance();
        Instant now = clock.instant();
        TellerSession session;
        try {
            session = sessions.saveAndFlush(new TellerSession(UuidV7.next(), tenantId, drawer.getBranchId(),
                    drawer.getId(), tellerId, businessDates.today(), drawer.getCurrency(), balance, now));
        } catch (DataIntegrityViolationException concurrent) {
            throw new BankingException(TellerErrorCode.DRAWER_IN_USE);
        }
        if (request.openingCount() != null) {
            BigDecimal counted = record(session, CashCount.Type.OPENING, request.openingCount(), tellerId, now);
            if (counted.compareTo(balance) != 0) {
                throw new BankingException(TellerErrorCode.OPENING_COUNT_MISMATCH);
            }
        }
        auditService.record(AuditEvent.builder("TELLER_SESSION_OPENED", RESOURCE)
                .resourceId(session.getId())
                .resourceReference(drawer.getCode())
                .branchId(drawer.getBranchId())
                .metadata("openingBalance", currencies.present(balance, drawer.getCurrency()))
                .build());
        return toResponse(session, drawer);
    }

    /**
     * The caller's open (or balancing) session.
     */
    @Transactional(readOnly = true)
    public SessionResponse mine() {
        UUID tenantId = TenantContext.requireTenantId();
        TellerSession session = sessions.findFirstByTenantIdAndTellerIdAndStatusIn(tenantId,
                        CurrentActor.require().id(), ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Open teller session"));
        return toResponse(session, cashPoints.drawer(session.getCashDrawerId()));
    }

    /**
     * Tellers see their own sessions; supervisors and cash viewers every session in their branches.
     */
    @Transactional(readOnly = true)
    public SessionResponse get(UUID sessionId) {
        return toResponse(visible(sessionId), null);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> ofDate(LocalDate date) {
        AuthenticatedActor actor = CurrentActor.require();
        LocalDate day = date != null ? date : businessDates.today();
        return sessions.findAllByTenantIdAndBusinessDateOrderByOpenedAt(TenantContext.requireTenantId(), day).stream()
                .filter(session -> actor.branchScope().permits(session.getBranchId()))
                .map(session -> toResponse(session, null))
                .toList();
    }

    /**
     * Blind count by the teller: the counted total is compared with the drawer's ledger balance. The session row is
     * locked first, so cash transactions in flight on the drawer finish before the balance is read and none can
     * start afterwards.
     */
    @Transactional
    public SessionResponse close(UUID sessionId, CloseSessionRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID tellerId = CurrentActor.require().id();
        TellerSession session = sessions.lockByTenantIdAndId(tenantId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Teller session"));
        if (!session.getTellerId().equals(tellerId)) {
            throw new BankingException(TellerErrorCode.NOT_YOUR_SESSION);
        }
        ConcurrentModificationException.assertVersion(request.version(), session.getVersion());
        if (!ACTIVE.contains(session.getStatus())) {
            throw new BankingException(TellerErrorCode.SESSION_NOT_OPEN);
        }
        CashDrawer drawer = cashPoints.drawer(session.getCashDrawerId());
        Instant now = clock.instant();
        BigDecimal expected = ledgerAccounts.lockBalance(drawer.getLedgerAccountId()).ledgerBalance();
        BigDecimal counted = record(session, CashCount.Type.CLOSING, request.count(), tellerId, now);
        session.count(expected, counted, blankToNull(request.note()), now);
        sessions.saveAndFlush(session);
        auditService.record(AuditEvent.builder(session.getStatus() == TellerSession.Status.CLOSED
                        ? "TELLER_SESSION_CLOSED" : "TELLER_SESSION_DIFFERENCE", RESOURCE)
                .resourceId(sessionId)
                .resourceReference(drawer.getCode())
                .branchId(drawer.getBranchId())
                .metadata("expected", currencies.present(expected, drawer.getCurrency()))
                .metadata("counted", currencies.present(counted, drawer.getCurrency()))
                .build());
        return toResponse(session, drawer);
    }

    /**
     * A supervisor (never the teller) accepts the difference: a shortage is moved from the drawer to the teller
     * shortages account, an overage from the drawer's surplus to other income, and the session closes.
     */
    @Transactional
    public SessionResponse acceptDifference(UUID sessionId, SupervisorDecisionRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        AuthenticatedActor supervisor = CurrentActor.require();
        TellerSession session = sessions.lockByTenantIdAndId(tenantId, sessionId)
                .filter(found -> supervisor.branchScope().permits(found.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Teller session"));
        ConcurrentModificationException.assertVersion(request.version(), session.getVersion());
        if (session.getStatus() != TellerSession.Status.BALANCING) {
            throw new BankingException(TellerErrorCode.SESSION_NOT_BALANCING);
        }
        if (supervisor.isSameUserAs(session.getTellerId())) {
            throw new BankingException(CommonErrorCode.FOUR_EYES_VIOLATION);
        }
        CashDrawer drawer = cashPoints.drawer(session.getCashDrawerId());
        BigDecimal difference = session.getDifference();
        BigDecimal amount = difference.abs();
        boolean shortage = difference.signum() < 0;
        String narration = (shortage ? "Teller shortage " : "Teller overage ") + drawer.getCode() + " "
                + session.getBusinessDate();
        List<PostingLine> lines = shortage
                ? List.of(PostingLine.toSystemAccount(SystemAccount.TELLER_SHORTAGES, drawer.getBranchId(),
                        drawer.getCurrency(), EntryDirection.DEBIT, amount, narration),
                PostingLine.toLedgerAccount(drawer.getLedgerAccountId(), EntryDirection.CREDIT, amount, narration))
                : List.of(PostingLine.toLedgerAccount(drawer.getLedgerAccountId(), EntryDirection.DEBIT, amount,
                        narration),
                PostingLine.toSystemAccount(SystemAccount.OTHER_INCOME, drawer.getBranchId(), drawer.getCurrency(),
                        EntryDirection.CREDIT, amount, narration));
        PostedJournal journal = postingEngine.post(new PostingRequest(JournalSource.TRANSACTION,
                References.next("TD", businessDates.today()), null, drawer.getBranchId(), null, narration, null,
                lines));
        Instant now = clock.instant();
        session.acceptDifference(supervisor.id(), journal.id(), request.note().trim(), now);
        sessions.saveAndFlush(session);
        auditService.record(AuditEvent.builder("TELLER_DIFFERENCE_ACCEPTED", RESOURCE)
                .resourceId(sessionId)
                .resourceReference(drawer.getCode())
                .branchId(drawer.getBranchId())
                .metadata("difference", currencies.present(difference, drawer.getCurrency()))
                .metadata("tellerId", session.getTellerId())
                .metadata("journalId", journal.id())
                .build());
        return toResponse(session, drawer);
    }

    // ------------------------------------------------------------------------------- for the transaction module

    /**
     * The drawer of the teller's open session, share-locked until the caller commits (a close waits for it).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CashDrawerRef drawerForCash(UUID tellerId) {
        TellerSession session = sessions.lockSharedByTeller(TenantContext.requireTenantId(), tellerId,
                        TellerSession.Status.OPEN)
                .orElseThrow(() -> new BankingException(TellerErrorCode.TELLER_SESSION_REQUIRED));
        return toRef(session);
    }

    /**
     * The drawer for an approved cash transaction: the maker's session on that drawer must still be open.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CashDrawerRef drawerForApprovedCash(UUID drawerId, UUID tellerId) {
        TellerSession session = sessions.lockSharedByDrawer(TenantContext.requireTenantId(), drawerId,
                        TellerSession.Status.OPEN)
                .filter(found -> found.getTellerId().equals(tellerId))
                .orElseThrow(() -> new BankingException(TellerErrorCode.TELLER_SESSION_REQUIRED,
                        "The teller who asked for this is no longer working the drawer."));
        return toRef(session);
    }

    /**
     * Any open session on the drawer (cash reversals and movements change the drawer's cash, so someone must be
     * working it).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CashDrawerRef requireOpenDrawer(UUID drawerId) {
        TellerSession session = sessions.lockSharedByDrawer(TenantContext.requireTenantId(), drawerId,
                        TellerSession.Status.OPEN)
                .orElseThrow(() -> new BankingException(TellerErrorCode.SESSION_NOT_OPEN,
                        "Nobody is working the drawer. Its teller must open a session first."));
        return toRef(session);
    }

    /**
     * Sessions not yet closed (for end-of-day).
     */
    @Transactional(readOnly = true)
    public List<String> unclosedSessions() {
        return sessions.findAllByTenantIdAndStatusIn(TenantContext.requireTenantId(), ACTIVE).stream()
                .map(session -> "Drawer " + cashPoints.drawer(session.getCashDrawerId()).getCode() + " is "
                        + session.getStatus().name().toLowerCase() + " (opened " + session.getBusinessDate() + ")")
                .toList();
    }

    // ---------------------------------------------------------------------------------------------------------

    private BigDecimal record(TellerSession session, CashCount.Type type, CashCountRequest request, UUID countedBy,
                             Instant at) {
        int minorUnits = currencies.require(session.getCurrency()).minorUnits();
        Map<String, Integer> sorted = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> denomination : request.denominations().entrySet()) {
            BigDecimal value = new BigDecimal(denomination.getKey());
            if (value.signum() <= 0 || value.stripTrailingZeros().scale() > minorUnits) {
                throw new BankingException(TellerErrorCode.INVALID_DENOMINATIONS);
            }
            sorted.put(value.toPlainString(), denomination.getValue());
            total = total.add(value.multiply(BigDecimal.valueOf(denomination.getValue())));
        }
        counts.saveAndFlush(new CashCount(UuidV7.next(), session.getTenantId(), session.getId(), type,
                jsonMapper.writeValueAsString(sorted), total, countedBy, at));
        return total;
    }

    private TellerSession visible(UUID sessionId) {
        AuthenticatedActor actor = CurrentActor.require();
        boolean overseer = actor.hasPermission(Permissions.TELLER_SUPERVISE) || actor.hasPermission(Permissions.CASH_VIEW);
        TellerSession session = sessions.findByTenantIdAndId(TenantContext.requireTenantId(), sessionId)
                .filter(found -> overseer ? actor.branchScope().permits(found.getBranchId())
                        : actor.isSameUserAs(found.getTellerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Teller session"));
        return session;
    }

    private CashDrawerRef toRef(TellerSession session) {
        CashDrawer drawer = cashPoints.drawer(session.getCashDrawerId());
        return new CashDrawerRef(drawer.getId(), session.getId(), drawer.getCode(), drawer.getLedgerAccountId(),
                drawer.getBranchId(), drawer.getCurrency(), session.getTellerId());
    }

    private SessionResponse toResponse(TellerSession session, CashDrawer known) {
        CashDrawer drawer = known != null ? known : cashPoints.drawer(session.getCashDrawerId());
        String currency = session.getCurrency();
        return new SessionResponse(session.getId(), drawer.getId(), drawer.getCode(), session.getBranchId(),
                session.getTellerId(), session.getBusinessDate(), session.getStatus().name(), currency,
                currencies.present(session.getOpeningBalance(), currency),
                ledgerAccounts.balance(drawer.getLedgerAccountId()).ledgerBalance(),
                present(session.getExpectedClosingBalance(), currency), present(session.getCountedBalance(), currency),
                present(session.getDifference(), currency), session.getOpenedAt(), session.getClosedAt(),
                session.getSupervisorId(), session.getCloseNote(), session.getVersion());
    }

    private BigDecimal present(BigDecimal amount, String currency) {
        return amount == null ? null : currencies.present(amount, currency);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
