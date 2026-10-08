package com.company.banking.ledger.service;

import com.company.banking.tenant.service.TenantService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The institution's current business date: today's date in its time zone.
 *
 * <p>Phase 3 (branch operations) replaces this with an explicitly managed business date that only end-of-day
 * processing advances; postings already take their business date from here, so nothing else changes then.
 */
@Service
@RequiredArgsConstructor
public class BusinessDateService {

    private final TenantService tenantService;
    private final Clock clock;

    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneId.of(tenantService.getCurrent().timezone())));
    }
}
