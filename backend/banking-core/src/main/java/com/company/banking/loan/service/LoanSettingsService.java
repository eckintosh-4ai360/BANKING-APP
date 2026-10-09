package com.company.banking.loan.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.loan.dto.LoanDtos;
import com.company.banking.loan.entity.LoanDelinquencyBand;
import com.company.banking.loan.exception.LoanErrorCode;
import com.company.banking.loan.repository.LoanDelinquencyBandRepository;
import com.company.banking.loan.schedule.LoanArrears;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The institution's loan settings: its delinquency bands. A new institution starts with a common five-band ladder;
 * it is a starting point to review against the institution's regulator, not a statement of any regulation.
 */
@Service
@RequiredArgsConstructor
public class LoanSettingsService {

    private static final String RESOURCE = "LOAN_SETTINGS";
    private static final List<LoanDtos.Band> DEFAULT_BANDS = List.of(
            new LoanDtos.Band("CURRENT", "Current", 0, new BigDecimal("1"), false),
            new LoanDtos.Band("WATCH", "Watch", 1, new BigDecimal("5"), false),
            new LoanDtos.Band("SUBSTANDARD", "Substandard", 31, new BigDecimal("25"), false),
            new LoanDtos.Band("DOUBTFUL", "Doubtful", 91, new BigDecimal("50"), true),
            new LoanDtos.Band("LOSS", "Loss", 181, new BigDecimal("100"), true));

    private final LoanDelinquencyBandRepository bands;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<LoanDtos.Band> bands() {
        return bands.findAllByIdTenantIdOrderByMinDays(TenantContext.requireTenantId()).stream()
                .map(band -> new LoanDtos.Band(band.getCode(), band.getName(), band.getMinDays(),
                        band.getProvisionRate().stripTrailingZeros(), band.isSuspendAccrual()))
                .toList();
    }

    /**
     * Replaces the bands. Loans move to their new band at the next end-of-day.
     */
    @Transactional
    public List<LoanDtos.Band> replaceBands(LoanDtos.Bands request) {
        List<LoanDtos.Band> requested = request.bands().stream()
                .sorted(Comparator.comparingInt(LoanDtos.Band::minDays)).toList();
        requireLadder(requested);
        List<LoanDtos.Band> before = bands();
        write(requested);
        List<LoanDtos.Band> after = bands();
        auditService.record(AuditEvent.builder("LOAN_DELINQUENCY_BANDS_REPLACED", RESOURCE)
                .before(before)
                .after(after)
                .build());
        return after;
    }

    /**
     * The default bands for an institution that has none. Idempotent.
     */
    @Transactional
    public void provisionDefaults() {
        if (bands.findAllByIdTenantIdOrderByMinDays(TenantContext.requireTenantId()).isEmpty()) {
            write(DEFAULT_BANDS);
        }
    }

    /**
     * The bands as end-of-day uses them.
     */
    @Transactional(readOnly = true)
    public List<LoanArrears.Band> ladder() {
        List<LoanArrears.Band> ladder = bands.findAllByIdTenantIdOrderByMinDays(TenantContext.requireTenantId())
                .stream()
                .map(band -> new LoanArrears.Band(band.getCode(), band.getMinDays(), band.getProvisionRate(),
                        band.isSuspendAccrual()))
                .toList();
        if (ladder.isEmpty() || ladder.getFirst().minDays() != 0) {
            throw new IllegalStateException("The institution has no delinquency band starting at 0 days");
        }
        return ladder;
    }

    private void write(List<LoanDtos.Band> ladder) {
        UUID tenantId = TenantContext.requireTenantId();
        bands.deleteAllOfTenant(tenantId);
        Instant now = clock.instant();
        bands.saveAllAndFlush(ladder.stream()
                .map(band -> new LoanDelinquencyBand(tenantId, band.code(), band.name().trim(), band.minDays(),
                        band.provisionRate(), band.suspendAccrual(), now))
                .toList());
    }

    /**
     * One band starts at 0 days; codes and starting days are unique; a later band never provisions less or resumes
     * accrual an earlier one suspended.
     */
    private static void requireLadder(List<LoanDtos.Band> ladder) {
        Set<String> codes = new HashSet<>();
        Set<Integer> starts = new HashSet<>();
        for (int index = 0; index < ladder.size(); index++) {
            LoanDtos.Band band = ladder.get(index);
            if (!codes.add(band.code()) || !starts.add(band.minDays())) {
                throw new BankingException(LoanErrorCode.INVALID_BANDS, "Band codes and starting days must be"
                        + " unique.");
            }
            if (index > 0) {
                LoanDtos.Band previous = ladder.get(index - 1);
                if (band.provisionRate().compareTo(previous.provisionRate()) < 0
                        || (previous.suspendAccrual() && !band.suspendAccrual())) {
                    throw new BankingException(LoanErrorCode.INVALID_BANDS);
                }
            }
        }
        if (ladder.getFirst().minDays() != 0) {
            throw new BankingException(LoanErrorCode.INVALID_BANDS, "One band must start at 0 days past due.");
        }
    }
}
