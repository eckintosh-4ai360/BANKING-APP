package com.company.banking.susu.service;

import com.company.banking.account.dto.PostingAccount;
import com.company.banking.account.service.AccountService;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.eod.EndOfDayContext;
import com.company.banking.common.eod.EndOfDayStep;
import com.company.banking.common.id.References;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.PostingRequest;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.JournalSource;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.ledger.service.PostingEngine;
import com.company.banking.product.service.ProductService;
import com.company.banking.susu.entity.SusuContribution;
import com.company.banking.susu.entity.SusuFrequency;
import com.company.banking.susu.entity.SusuPlan;
import com.company.banking.susu.repository.SusuCommissionRepository;
import com.company.banking.susu.repository.SusuContributionRepository;
import com.company.banking.susu.repository.SusuPlanRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * End-of-day for susu plans ({@code SUSU_CONTRIBUTIONS}, after interest), for the closed business date:
 * <ol>
 *   <li>contributions due on or before it and still unpaid become missed;</li>
 *   <li>for each cycle whose last contribution was due on or before it, the commission is charged once: the
 *   plan's commission contributions, but no more than were paid in the cycle, and no more than the account holds
 *   (Dr the account, Cr the product's fee income); then the next cycle is scheduled, or the plan completes at its
 *   end date.</li>
 * </ol>
 * Each plan is handled under its row lock and every effect is keyed (contribution status, commission per cycle), so
 * a resumed run does nothing twice.
 */
@Service
@RequiredArgsConstructor
public class SusuEndOfDayService implements EndOfDayStep {

    static final String STEP = "SUSU_CONTRIBUTIONS";
    private static final int BATCH = 200;
    private static final UUID FIRST = new UUID(0, 0);

    private final SusuPlanRepository plans;
    private final SusuContributionRepository contributions;
    private final SusuCommissionRepository commissions;
    private final SusuPlanService planService;
    private final AccountService accountService;
    private final ProductService productService;
    private final LedgerAccountService ledgerAccounts;
    private final PostingEngine postingEngine;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    public String code() {
        return STEP;
    }

    @Override
    public int order() {
        return 250;
    }

    @Override
    public Map<String, Object> run(EndOfDayContext context) {
        UUID tenantId = TenantContext.requireTenantId();
        LocalDate closed = context.businessDate();
        int[] totals = new int[3]; // missed, cycles closed, plans completed
        UUID after = FIRST;
        while (true) {
            UUID cursor = after;
            List<UUID> batch = context.inTransaction(() -> plans.idsWithStatus(tenantId, SusuPlan.Status.ACTIVE,
                    cursor, Limit.of(BATCH)));
            if (batch.isEmpty()) {
                break;
            }
            context.inTransaction(() -> {
                batch.forEach(planId -> close(tenantId, planId, closed, totals));
                return null;
            });
            after = batch.getLast();
            context.checkpoint(STEP, "batch");
        }
        return Map.of("missed", totals[0], "cyclesClosed", totals[1], "plansCompleted", totals[2]);
    }

    private void close(UUID tenantId, UUID planId, LocalDate closed, int[] totals) {
        SusuPlan plan = plans.lockByTenantIdAndId(tenantId, planId).orElseThrow();
        if (!plan.isActive()) {
            return;
        }
        SusuFrequency frequency = planService.frequencyOf(plan);
        while (plan.isActive()) {
            for (SusuContribution due : contributions.findExpectedDueBy(tenantId, planId, closed)) {
                due.miss();
                contributions.save(due);
                totals[0]++;
            }
            List<SusuContribution> cycle = contributions.findAllByTenantIdAndPlanIdAndCycleNoOrderBySequenceNo(
                    tenantId, planId, plan.getCurrentCycle());
            if (cycle.isEmpty() || cycle.getLast().getDueDate().isAfter(closed)) {
                break;
            }
            chargeCommission(plan, cycle, closed);
            totals[1]++;
            int next = plan.getCurrentCycle() + 1;
            if (planService.scheduleCycle(plan, frequency, next) == 0) {
                plan.close(SusuPlan.Status.COMPLETED, clock.instant(), "Reached its end date");
                totals[2]++;
                auditService.record(AuditEvent.builder("SUSU_PLAN_COMPLETED", SusuPlanService.RESOURCE)
                        .resourceId(planId)
                        .resourceReference(plan.getPlanNumber())
                        .branchId(plan.getBranchId())
                        .build());
            }
            plans.saveAndFlush(plan);
        }
        contributions.flush();
    }

    /**
     * The cycle's commission: its commission contributions, at most as many as were paid, at most what the account
     * holds (and nothing from an account that cannot pay out). Recorded once per cycle, even when nothing is charged.
     */
    private void chargeCommission(SusuPlan plan, List<SusuContribution> cycle, LocalDate closed) {
        int cycleNo = plan.getCurrentCycle();
        if (commissions.exists(plan.getTenantId(), plan.getId(), cycleNo)) {
            return;
        }
        long paid = cycle.stream().filter(c -> c.getStatus() == SusuContribution.Status.PAID).count();
        BigDecimal due = plan.getContributionAmount()
                .multiply(BigDecimal.valueOf(Math.min(paid, plan.getCommissionContributions())));
        PostingAccount account = accountService.lockForPosting(List.of(plan.getAccountId())).get(plan.getAccountId());
        BigDecimal available = account.debitAllowed()
                ? ledgerAccounts.balance(account.ledgerAccountId()).availableBalance().max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        BigDecimal charged = due.min(available);
        UUID journalId = null;
        if (charged.signum() > 0) {
            String narration = "Susu commission, cycle " + cycleNo + " of " + plan.getPlanNumber();
            UUID feeGl = productService.terms(account.productVersionId()).feeIncomeGlId();
            journalId = postingEngine.postForClosedDate(new PostingRequest(JournalSource.EOD,
                    References.next("SC", closed), null, account.branchId(), null, narration, null, List.of(
                    PostingLine.toLedgerAccount(account.ledgerAccountId(), EntryDirection.DEBIT, charged, narration),
                    PostingLine.toGl(feeGl, account.branchId(), plan.getCurrency(), EntryDirection.CREDIT, charged,
                            narration))), closed).id();
        }
        commissions.insert(plan.getTenantId(), plan.getId(), cycleNo, due, charged, journalId, closed,
                clock.instant());
    }
}
