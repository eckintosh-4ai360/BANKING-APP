package com.company.banking.ledger.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.AccountingPeriodResponse;
import com.company.banking.ledger.exception.LedgerErrorCode;
import com.company.banking.ledger.repository.AccountingPeriodRepository;
import com.company.banking.ledger.repository.AccountingPeriodRepository.PeriodRow;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly accounting periods. A month's period opens on its first posting; once closed it never reopens, and
 * corrections post into the current period (spec risk F11).
 */
@Service
@RequiredArgsConstructor
public class AccountingPeriodService {

    private static final String RESOURCE = "ACCOUNTING_PERIOD";

    private final AccountingPeriodRepository repository;
    private final BusinessDateService businessDates;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Ensures the period covering {@code date} is open, opening that month if it has no period yet. A month on or
     * before the latest closed period can never be opened.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireOpen(LocalDate date) {
        UUID tenantId = TenantContext.requireTenantId();
        PeriodRow period = repository.findCovering(tenantId, date).orElse(null);
        if (period == null) {
            if (repository.latestClosedEnd(tenantId).map(end -> !date.isAfter(end)).orElse(false)) {
                throw new BankingException(LedgerErrorCode.PERIOD_CLOSED);
            }
            LocalDate start = date.withDayOfMonth(1);
            repository.insertOpenIfAbsent(tenantId, start, start.plusMonths(1).minusDays(1));
            period = repository.findCovering(tenantId, date).orElseThrow();
        }
        if (!"OPEN".equals(period.status())) {
            throw new BankingException(LedgerErrorCode.PERIOD_CLOSED);
        }
    }

    /**
     * Opens the current month for a new institution.
     */
    @Transactional
    public void provisionCurrentPeriod() {
        requireOpen(businessDates.today());
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountingPeriodResponse> list(PageRequest page) {
        UUID tenantId = TenantContext.requireTenantId();
        return PageResponse.of(repository.list(tenantId, page.getPageSize(), page.getOffset()).stream()
                        .map(AccountingPeriodService::toResponse).toList(),
                page.getPageNumber(), page.getPageSize(), repository.count(tenantId));
    }

    /**
     * Closes a period that has ended, oldest first. Postings already in flight in that period finish first: they
     * hold a share lock on the period row that this update waits for.
     */
    @Transactional
    public AccountingPeriodResponse close(LocalDate periodStart) {
        UUID tenantId = TenantContext.requireTenantId();
        PeriodRow period = repository.lockForUpdate(tenantId, periodStart)
                .orElseThrow(() -> new ResourceNotFoundException("Accounting period"));
        if (!"OPEN".equals(period.status())
                || !period.periodEnd().isBefore(businessDates.today())
                || repository.hasUnclosedBefore(tenantId, periodStart)) {
            throw new BankingException(LedgerErrorCode.PERIOD_CLOSE_NOT_ALLOWED);
        }
        UUID actorId = CurrentActor.currentActorId().orElse(null);
        repository.close(tenantId, periodStart, clock.instant(), actorId);
        AccountingPeriodResponse closed = repository.lockForUpdate(tenantId, periodStart)
                .map(AccountingPeriodService::toResponse).orElseThrow();
        auditService.record(AuditEvent.builder("ACCOUNTING_PERIOD_CLOSED", RESOURCE)
                .resourceId(periodStart.toString())
                .before(Map.of("status", period.status()))
                .after(closed)
                .build());
        return closed;
    }

    private static AccountingPeriodResponse toResponse(PeriodRow row) {
        return new AccountingPeriodResponse(row.periodStart(), row.periodEnd(), row.status(), row.closedAt(),
                row.closedBy());
    }
}
