package com.company.banking.ledger.service;

import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.repository.BusinessDayRepository;
import com.company.banking.ledger.repository.BusinessDayRepository.BusinessDay;
import com.company.banking.tenant.service.TenantService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The institution's business date: the date every posting carries. It is stored per institution and only
 * end-of-day processing moves it forward (to the next working day of the institution's calendar), so a day's
 * books stay that day's books however long the branches stay open.
 */
@Service
@RequiredArgsConstructor
public class BusinessDateService {

    private final BusinessDayRepository repository;
    private final TenantService tenantService;
    private final Clock clock;

    /**
     * The current business date. An institution onboarded before business dates were stored, and not yet
     * backfilled, falls back to today's calendar date in its time zone.
     */
    @Transactional(readOnly = true)
    public LocalDate today() {
        return repository.find(TenantContext.requireTenantId()).map(BusinessDay::businessDate)
                .orElseGet(this::calendarToday);
    }

    /**
     * The business date a posting carries. The date row stays share-locked until the posting commits, so the
     * end-of-day roll waits for postings in flight and every later posting carries the new date: end-of-day for a
     * date never misses a journal of that date.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDate forPosting() {
        return repository.lockShared(TenantContext.requireTenantId()).map(BusinessDay::businessDate)
                .orElseGet(this::calendarToday);
    }

    /**
     * The date for an end-of-day journal: the date being closed, which must be the one the business date just rolled
     * from (share-locked like {@link #forPosting()}).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDate forEndOfDayPosting(LocalDate closedDate) {
        LocalDate previous = repository.lockShared(TenantContext.requireTenantId())
                .map(BusinessDay::previousBusinessDate)
                .orElse(null);
        if (!closedDate.equals(previous)) {
            throw new IllegalStateException("End-of-day postings are only possible for the date that was just closed");
        }
        return closedDate;
    }

    /**
     * The business date before the last end-of-day roll, if any.
     */
    @Transactional(readOnly = true)
    public LocalDate previous() {
        return repository.find(TenantContext.requireTenantId()).map(BusinessDay::previousBusinessDate).orElse(null);
    }

    /**
     * Starts the institution's business date at today's calendar date (onboarding and backfill). Idempotent.
     */
    @Transactional
    public void provision() {
        repository.insertIfAbsent(TenantContext.requireTenantId(), calendarToday(), clock.instant());
    }

    /**
     * Moves the business date from {@code expected} to {@code next} (end-of-day). The row stays locked until the
     * caller commits; a roll from a date that is no longer current is refused, so two runs cannot both roll.
     *
     * @return the business date that was current before the roll
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDate roll(LocalDate expected, LocalDate next) {
        UUID tenantId = TenantContext.requireTenantId();
        BusinessDay current = repository.lock(tenantId)
                .orElseThrow(() -> new IllegalStateException("The institution has no business date"));
        if (!current.businessDate().equals(expected)) {
            throw new BankingException(CommonErrorCode.CONCURRENT_MODIFICATION,
                    "The business date has already moved to " + current.businessDate() + ".");
        }
        if (!next.isAfter(expected)) {
            throw new IllegalArgumentException("The next business date must be after " + expected);
        }
        repository.advance(tenantId, next, expected, clock.instant(), CurrentActor.currentActorId().orElse(null));
        return expected;
    }

    private LocalDate calendarToday() {
        return LocalDate.now(clock.withZone(ZoneId.of(tenantService.getCurrent().timezone())));
    }
}
