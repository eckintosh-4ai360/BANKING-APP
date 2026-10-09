package com.company.banking.teller.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.eod.EndOfDayContext;
import com.company.banking.common.eod.EndOfDayStep;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.teller.dto.CashPositionResponse;
import com.company.banking.teller.entity.CashDrawer;
import com.company.banking.teller.entity.Vault;
import com.company.banking.teller.repository.CashDrawerRepository;
import com.company.banking.teller.repository.CashPositionRepository;
import com.company.banking.teller.repository.CashPositionRepository.LastCount;
import com.company.banking.teller.repository.CashPositionRepository.Position;
import com.company.banking.teller.repository.VaultRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-of-day cash reconciliation ({@code CASH_RECONCILIATION}, after the steps that post for the closed date and
 * before the GL snapshot). For the closed date it records every vault's and drawer's ledger balance next to the
 * cash last counted in it. A drawer's ledger balance must equal its last closing count, because nothing may post to
 * a drawer without an open session; a difference is a {@code BREAK}, audited for investigation. Breaks do not stop
 * end-of-day: the books still balance, the cash does not, and that is for people to resolve.
 */
@Service
@RequiredArgsConstructor
public class CashReconciliationService implements EndOfDayStep {

    static final String STEP = "CASH_RECONCILIATION";

    private final VaultRepository vaults;
    private final CashDrawerRepository drawers;
    private final CashPositionRepository positions;
    private final LedgerAccountService ledgerAccounts;
    private final CurrencyService currencies;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    public String code() {
        return STEP;
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public Map<String, Object> run(EndOfDayContext context) {
        UUID tenantId = TenantContext.requireTenantId();
        LocalDate closed = context.businessDate();
        List<Position> written = context.inTransaction(() -> {
            reconcile(tenantId, closed);
            return positions.forDate(tenantId, closed);
        });
        context.checkpoint(STEP, "written");
        return Map.of("cashPoints", written.size(),
                "breaks", written.stream().filter(position -> "BREAK".equals(position.status())).count(),
                "notCounted", written.stream().filter(position -> "NOT_COUNTED".equals(position.status())).count());
    }

    private void reconcile(UUID tenantId, LocalDate closed) {
        for (Vault vault : vaults.findAllByTenantIdOrderByBranchIdAscCurrencyAsc(tenantId)) {
            if (vault.getStatus() != Vault.Status.CLOSED) {
                positions.insert(tenantId, closed, new Position(vault.getId(), "VAULT", vault.getBranchId(),
                        vault.getCurrency(), ledgerAccounts.balanceAt(vault.getLedgerAccountId(), closed), null,
                        null, null, null, "NOT_COUNTED"), clock.instant());
            }
        }
        Map<UUID, LastCount> counts = positions.lastCounts(tenantId, closed);
        for (CashDrawer drawer : drawers.findAllByTenantIdOrderByBranchIdAscCodeAsc(tenantId)) {
            if (drawer.getStatus() == CashDrawer.Status.CLOSED) {
                continue;
            }
            BigDecimal ledger = ledgerAccounts.balanceAt(drawer.getLedgerAccountId(), closed);
            LastCount count = counts.get(drawer.getId());
            Position position;
            if (count == null) {
                position = new Position(drawer.getId(), "DRAWER", drawer.getBranchId(), drawer.getCurrency(), ledger,
                        null, null, null, null, "NOT_COUNTED");
            } else {
                BigDecimal difference = count.counted().subtract(ledger);
                position = new Position(drawer.getId(), "DRAWER", drawer.getBranchId(), drawer.getCurrency(), ledger,
                        count.counted(), count.sessionId(), count.closedAt(), difference,
                        difference.signum() == 0 ? "MATCHED" : "BREAK");
            }
            if (positions.insert(tenantId, closed, position, clock.instant()) && "BREAK".equals(position.status())) {
                auditService.record(AuditEvent.builder("CASH_RECONCILIATION_BREAK", CashPointService.DRAWER_RESOURCE)
                        .resourceId(drawer.getId())
                        .resourceReference(drawer.getCode())
                        .branchId(drawer.getBranchId())
                        .metadata("businessDate", closed.toString())
                        .metadata("ledgerBalance", ledger.toPlainString())
                        .metadata("countedBalance", count.counted().toPlainString())
                        .metadata("tellerSessionId", count.sessionId().toString())
                        .build());
            }
        }
    }

    /**
     * The cash positions of a business date (the latest reconciled date by default) in the caller's branches.
     */
    @Transactional(readOnly = true)
    public List<CashPositionResponse> positions(LocalDate date, UUID branchId) {
        UUID tenantId = TenantContext.requireTenantId();
        LocalDate day = date != null ? date : positions.latestDate(tenantId).orElse(null);
        if (day == null) {
            return List.of();
        }
        BranchScope scope = CurrentActor.require().branchScope();
        return positions.forDate(tenantId, day).stream()
                .filter(position -> scope.permits(position.branchId()))
                .filter(position -> branchId == null || position.branchId().equals(branchId))
                .map(position -> toResponse(day, position))
                .toList();
    }

    private CashPositionResponse toResponse(LocalDate date, Position position) {
        int scale = currencies.require(position.currency()).minorUnits();
        return new CashPositionResponse(date, position.cashPointId(), position.cashPointType(), position.branchId(),
                position.currency(), money(position.ledgerBalance(), scale), money(position.countedBalance(), scale),
                position.tellerSessionId(), position.countedAt(), money(position.difference(), scale),
                position.status());
    }

    private static BigDecimal money(BigDecimal amount, int scale) {
        return amount == null ? null : amount.setScale(scale, RoundingMode.UNNECESSARY);
    }
}
