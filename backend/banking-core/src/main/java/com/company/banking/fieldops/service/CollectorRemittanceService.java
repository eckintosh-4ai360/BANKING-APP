package com.company.banking.fieldops.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.id.References;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.idempotency.IdempotencyService;
import com.company.banking.common.idempotency.IdempotencyService.Result;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.fieldops.dto.RemittanceDtos;
import com.company.banking.fieldops.entity.CollectorRemittance;
import com.company.banking.fieldops.entity.FieldOfficer;
import com.company.banking.fieldops.exception.FieldErrorCode;
import com.company.banking.fieldops.repository.CollectorRemittanceRepository;
import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.PostingRequest;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.JournalSource;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.ledger.service.PostingEngine;
import com.company.banking.teller.dto.CashDrawerRef;
import com.company.banking.teller.service.TellerSessionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Field officers hand the cash they collected to a teller: Dr the teller's drawer, Cr the officer's cash with
 * collectors. The teller counts it and records the amount; the teller is never the officer, the officer can never
 * hand over more than they carry (the account is balance-checked), and a retried request posts once.
 */
@Service
@RequiredArgsConstructor
public class CollectorRemittanceService {

    static final String RESOURCE = "COLLECTOR_REMITTANCE";

    private final CollectorRemittanceRepository remittances;
    private final FieldOfficerService officerService;
    private final FieldScope fieldScope;
    private final TellerSessionService tellerSessions;
    private final PostingEngine postingEngine;
    private final LedgerAccountService ledgerAccounts;
    private final CurrencyService currencies;
    private final BusinessDateService businessDates;
    private final IdempotencyService idempotency;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public Result<RemittanceDtos.Remittance> remit(String idempotencyKey, RemittanceDtos.Remit request) {
        IdempotencyService.requireValidKey(idempotencyKey);
        AuthenticatedActor teller = CurrentActor.require();
        String scope = teller.type() + ":" + teller.id() + ":" + RESOURCE;
        return idempotency.execute(new IdempotencyService.Request(scope, idempotencyKey, idempotency.hash(request),
                RESOURCE), RemittanceDtos.Remittance.class, RemittanceDtos.Remittance::id,
                () -> receive(teller, idempotencyKey, request));
    }

    @Transactional(readOnly = true)
    public PageResponse<RemittanceDtos.Remittance> search(UUID officerId, PageRequest page) {
        Set<UUID> visible = fieldScope.officers(officerId);
        if (visible.isEmpty()) {
            return PageResponse.of(List.of(), page.getPageNumber(), page.getPageSize(), 0);
        }
        return PageResponse.from(remittances.search(TenantContext.requireTenantId(), visible, page),
                remittance -> toResponse(remittance, null, null));
    }

    private RemittanceDtos.Remittance receive(AuthenticatedActor teller, String idempotencyKey,
                                              RemittanceDtos.Remit request) {
        FieldOfficer officer = officerService.loadInScope(request.officerId());
        if (teller.isSameUserAs(officer.getStaffId())) {
            throw new BankingException(CommonErrorCode.FOUR_EYES_VIOLATION,
                    "A field officer cannot receive their own cash.");
        }
        CashDrawerRef drawer = tellerSessions.drawerForCash(teller.id());
        if (!drawer.currency().equals(officer.getCurrency())) {
            throw new BankingException(FieldErrorCode.CURRENCY_MISMATCH);
        }
        currencies.requireValidAmount(request.amount(), officer.getCurrency());
        BigDecimal amount = currencies.present(request.amount(), officer.getCurrency());
        BigDecimal carried = ledgerAccounts.balance(officer.getCollectorLedgerAccountId()).ledgerBalance();
        if (carried.compareTo(amount) < 0) {
            throw new BankingException(FieldErrorCode.REMITTANCE_ABOVE_CASH);
        }
        String reference = References.next("REM", businessDates.today());
        String narration = "Collector remittance";
        PostedJournal journal = postingEngine.post(new PostingRequest(JournalSource.TRANSACTION, reference, null,
                drawer.branchId(), null, narration, null, List.of(
                PostingLine.toLedgerAccount(drawer.ledgerAccountId(), EntryDirection.DEBIT, amount, narration),
                PostingLine.toLedgerAccount(officer.getCollectorLedgerAccountId(), EntryDirection.CREDIT, amount,
                        narration))));
        CollectorRemittance remittance = remittances.saveAndFlush(CollectorRemittance.builder()
                .id(UuidV7.next())
                .tenantId(officer.getTenantId())
                .reference(reference)
                .officerId(officer.getStaffId())
                .tellerId(teller.id())
                .tellerSessionId(drawer.sessionId())
                .cashDrawerId(drawer.drawerId())
                .branchId(drawer.branchId())
                .amount(amount)
                .currency(officer.getCurrency())
                .journalEntryId(journal.id())
                .businessDate(journal.businessDate())
                .note(request.note() == null || request.note().isBlank() ? null : request.note().trim())
                .idempotencyKey(idempotencyKey)
                .createdAt(clock.instant())
                .build());
        BigDecimal after = journal.balanceOf(officer.getCollectorLedgerAccountId()).ledgerBalance();
        RemittanceDtos.Remittance response = toResponse(remittance, drawer.code(), after);
        auditService.record(AuditEvent.builder("COLLECTOR_REMITTANCE_RECEIVED", RESOURCE)
                .resourceId(remittance.getId())
                .resourceReference(reference)
                .branchId(drawer.branchId())
                .after(response)
                .build());
        return response;
    }

    private RemittanceDtos.Remittance toResponse(CollectorRemittance remittance, String drawerCode,
                                                 BigDecimal officerCashAfter) {
        String currency = remittance.getCurrency();
        return new RemittanceDtos.Remittance(remittance.getId(), remittance.getReference(),
                remittance.getOfficerId(), remittance.getTellerId(), remittance.getCashDrawerId(), drawerCode,
                remittance.getBranchId(), currencies.present(remittance.getAmount(), currency), currency,
                remittance.getBusinessDate(), remittance.getNote(), remittance.getCreatedAt(),
                officerCashAfter == null ? null : currencies.present(officerCashAfter, currency));
    }
}
