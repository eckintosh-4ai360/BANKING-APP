package com.company.banking.susu.service;

import com.company.banking.account.dto.AccountSummary;
import com.company.banking.account.service.AccountService;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.References;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.service.CustomerService;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.susu.dto.SusuDtos;
import com.company.banking.susu.entity.SusuContribution;
import com.company.banking.susu.entity.SusuFrequency;
import com.company.banking.susu.entity.SusuPlan;
import com.company.banking.susu.exception.SusuErrorCode;
import com.company.banking.susu.repository.SusuCommissionRepository;
import com.company.banking.susu.repository.SusuContributionRepository;
import com.company.banking.susu.repository.SusuFrequencyRepository;
import com.company.banking.susu.repository.SusuPlanRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Susu plans: a customer contributes a fixed amount at a fixed frequency into their susu account. Contributions are
 * scheduled one cycle at a time; a collection pays the oldest unpaid ones (a missed contribution can still be paid
 * late). End-of-day ({@link SusuEndOfDayService}) marks what fell due unpaid as missed, charges the cycle's
 * commission and opens the next cycle.
 */
@Service
@RequiredArgsConstructor
public class SusuPlanService {

    static final String RESOURCE = "SUSU_PLAN";
    private static final Set<String> OPEN_ACCOUNT = Set.of("PENDING", "ACTIVE");

    private final SusuFrequencyRepository frequencies;
    private final SusuPlanRepository plans;
    private final SusuContributionRepository contributions;
    private final SusuCommissionRepository commissions;
    private final CustomerService customerService;
    private final AccountService accountService;
    private final CurrencyService currencies;
    private final BusinessDateService businessDates;
    private final AuditService auditService;
    private final Clock clock;

    // ------------------------------------------------------------------------------------------- frequencies

    @Transactional(readOnly = true)
    public List<SusuDtos.Frequency> frequencies() {
        return frequencies.findAllByIdTenantIdOrderByIdCode(TenantContext.requireTenantId()).stream()
                .map(SusuPlanService::toResponse).toList();
    }

    @Transactional
    public SusuDtos.Frequency createFrequency(SusuDtos.NewFrequency request) {
        UUID tenantId = TenantContext.requireTenantId();
        if (frequencies.existsById(new SusuFrequency.Key(tenantId, request.code()))) {
            throw new BankingException(SusuErrorCode.FREQUENCY_EXISTS);
        }
        SusuFrequency frequency = frequencies.saveAndFlush(new SusuFrequency(tenantId, request.code(),
                request.name().trim(), SusuFrequency.Unit.valueOf(request.intervalUnit()), request.intervalCount(),
                clock.instant()));
        SusuDtos.Frequency response = toResponse(frequency);
        auditService.record(AuditEvent.builder("SUSU_FREQUENCY_CREATED", "SUSU_FREQUENCY")
                .resourceReference(frequency.getCode())
                .after(response)
                .build());
        return response;
    }

    /**
     * Daily, weekly and monthly, for a new institution. Idempotent.
     */
    @Transactional
    public void provisionDefaults() {
        UUID tenantId = TenantContext.requireTenantId();
        Instant now = clock.instant();
        for (Object[] row : new Object[][]{{"DAILY", "Daily", SusuFrequency.Unit.DAY},
                {"WEEKLY", "Weekly", SusuFrequency.Unit.WEEK}, {"MONTHLY", "Monthly", SusuFrequency.Unit.MONTH}}) {
            String code = (String) row[0];
            if (!frequencies.existsById(new SusuFrequency.Key(tenantId, code))) {
                frequencies.save(new SusuFrequency(tenantId, code, (String) row[1], (SusuFrequency.Unit) row[2], 1,
                        now));
            }
        }
        frequencies.flush();
    }

    // ------------------------------------------------------------------------------------------------- plans

    @Transactional
    public SusuDtos.PlanDetail open(SusuDtos.OpenPlan request) {
        UUID tenantId = TenantContext.requireTenantId();
        CustomerSummary customer = customerService.getSummary(request.customerId());
        AccountSummary account = accountService.heldBy(customer.id()).stream()
                .filter(held -> held.id().equals(request.accountId()))
                .filter(held -> "SUSU".equals(held.productType()) && OPEN_ACCOUNT.contains(held.status()))
                .findFirst()
                .orElseThrow(() -> new BankingException(SusuErrorCode.NOT_A_SUSU_ACCOUNT));
        SusuFrequency frequency = frequencies.findById(new SusuFrequency.Key(tenantId, request.frequencyCode()))
                .filter(SusuFrequency::isActive)
                .orElseThrow(() -> new BankingException(SusuErrorCode.FREQUENCY_NOT_AVAILABLE));
        LocalDate today = businessDates.today();
        LocalDate start = request.startDate() != null ? request.startDate() : today;
        if (request.commissionContributions() >= request.cycleLength() || start.isBefore(today)
                || (request.endDate() != null && !request.endDate().isAfter(start))) {
            throw new BankingException(SusuErrorCode.INVALID_PLAN);
        }
        String currency = account.currency();
        currencies.requireValidAmount(request.contributionAmount(), currency);
        if (request.targetAmount() != null) {
            currencies.requireValidAmount(request.targetAmount(), currency);
        }
        if (plans.findAllByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenantId, customer.id()).stream()
                .anyMatch(plan -> plan.isActive() && plan.getAccountId().equals(account.id()))) {
            throw new BankingException(SusuErrorCode.PLAN_ALREADY_RUNNING);
        }
        SusuPlan plan = plans.saveAndFlush(SusuPlan.builder()
                .id(UuidV7.next())
                .tenantId(tenantId)
                .planNumber(References.next("SUSU", today))
                .customerId(customer.id())
                .accountId(account.id())
                .branchId(account.branchId())
                .frequencyCode(frequency.getCode())
                .contributionAmount(currencies.present(request.contributionAmount(), currency))
                .currency(currency)
                .cycleLength(request.cycleLength())
                .commissionContributions(request.commissionContributions())
                .startDate(start)
                .endDate(request.endDate())
                .targetAmount(request.targetAmount() == null ? null
                        : currencies.present(request.targetAmount(), currency))
                .createdAt(clock.instant())
                .createdBy(CurrentActor.currentActorId().orElse(null))
                .build());
        scheduleCycle(plan, frequency, 1);
        SusuDtos.PlanDetail detail = detail(plan);
        auditService.record(AuditEvent.builder("SUSU_PLAN_OPENED", RESOURCE)
                .resourceId(plan.getId())
                .resourceReference(plan.getPlanNumber())
                .branchId(plan.getBranchId())
                .after(detail.plan())
                .build());
        return detail;
    }

    @Transactional(readOnly = true)
    public SusuDtos.PlanDetail get(UUID planId) {
        return detail(loadInScope(planId));
    }

    @Transactional(readOnly = true)
    public PageResponse<SusuDtos.Plan> search(UUID customerId, String status, PageRequest page) {
        BranchScope scope = CurrentActor.require().branchScope();
        Collection<UUID> branches = scope.allBranches() || scope.branchIds().isEmpty()
                ? Set.of(new UUID(0, 0)) : scope.branchIds();
        return PageResponse.from(plans.search(TenantContext.requireTenantId(), scope.allBranches(), branches,
                        customerId, status == null ? null : SusuPlan.Status.valueOf(status), page),
                plan -> summary(plan, contributions.findAllByTenantIdAndPlanIdOrderBySequenceNo(plan.getTenantId(),
                        plan.getId())));
    }

    @Transactional
    public SusuDtos.PlanDetail cancel(UUID planId, SusuDtos.Close request) {
        SusuPlan plan = lockInScope(planId);
        if (!plan.getVersion().equals(request.version())) {
            throw new BankingException(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
        if (!plan.isActive()) {
            throw new BankingException(SusuErrorCode.PLAN_NOT_ACTIVE);
        }
        plan.close(SusuPlan.Status.CANCELLED, clock.instant(), request.reason().trim());
        plans.saveAndFlush(plan);
        auditService.record(AuditEvent.builder("SUSU_PLAN_CANCELLED", RESOURCE)
                .resourceId(planId)
                .resourceReference(plan.getPlanNumber())
                .branchId(plan.getBranchId())
                .metadata("reason", request.reason().trim())
                .build());
        return detail(plan);
    }

    /**
     * Excuses a contribution (e.g. the customer was in hospital); it is no longer owed.
     */
    @Transactional
    public SusuDtos.PlanDetail waive(UUID planId, int sequenceNo, SusuDtos.Waive request) {
        SusuPlan plan = lockInScope(planId);
        SusuContribution contribution = contributions.findAllByTenantIdAndPlanIdOrderBySequenceNo(plan.getTenantId(),
                        planId).stream()
                .filter(candidate -> candidate.getSequenceNo() == sequenceNo)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Contribution"));
        if (!contribution.isUnpaid()) {
            throw new BankingException(SusuErrorCode.CONTRIBUTION_NOT_UNPAID);
        }
        contribution.waive(CurrentActor.currentActorId().orElse(null), request.reason().trim());
        contributions.saveAndFlush(contribution);
        auditService.record(AuditEvent.builder("SUSU_CONTRIBUTION_WAIVED", RESOURCE)
                .resourceId(planId)
                .resourceReference(plan.getPlanNumber())
                .branchId(plan.getBranchId())
                .metadata("sequenceNo", sequenceNo)
                .metadata("reason", request.reason().trim())
                .build());
        return detail(plan);
    }

    // ----------------------------------------------------------------------------- for field collections

    /**
     * Records a field collection against a plan: the amount must be a whole number of contributions, and pays that
     * many of the oldest unpaid ones. Throws (rolling back the caller's posting) when the plan is not running, not
     * the customer's and account's, or not owed that much.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyCollection(UUID planId, UUID customerId, UUID accountId, BigDecimal amount, UUID collectionId,
                                UUID transactionId, Instant paidAt) {
        SusuPlan plan = plans.lockByTenantIdAndId(TenantContext.requireTenantId(), planId)
                .orElseThrow(() -> new BankingException(SusuErrorCode.PLAN_MISMATCH));
        if (!plan.getCustomerId().equals(customerId) || !plan.getAccountId().equals(accountId)) {
            throw new BankingException(SusuErrorCode.PLAN_MISMATCH);
        }
        if (!plan.isActive()) {
            throw new BankingException(SusuErrorCode.PLAN_NOT_ACTIVE);
        }
        BigDecimal[] division = amount.divideAndRemainder(plan.getContributionAmount());
        if (division[1].signum() != 0) {
            throw new BankingException(SusuErrorCode.AMOUNT_NOT_WHOLE_CONTRIBUTIONS);
        }
        int count = division[0].setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        List<SusuContribution> unpaid = contributions.findUnpaid(plan.getTenantId(), planId);
        if (count > unpaid.size()) {
            throw new BankingException(SusuErrorCode.OVERPAID);
        }
        for (SusuContribution contribution : unpaid.subList(0, count)) {
            contribution.pay(paidAt, collectionId, transactionId);
        }
        contributions.saveAll(unpaid.subList(0, count));
        contributions.flush();
    }

    /**
     * The customers' running plans, as the field app keeps them.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<SusuDtos.CollectablePlan>> collectablePlans(Collection<UUID> customerIds) {
        UUID tenantId = TenantContext.requireTenantId();
        Map<UUID, List<SusuDtos.CollectablePlan>> result = new HashMap<>();
        for (UUID customerId : customerIds) {
            List<SusuDtos.CollectablePlan> running = new ArrayList<>();
            for (SusuPlan plan : plans.findAllByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenantId, customerId)) {
                if (!plan.isActive()) {
                    continue;
                }
                List<SusuContribution> unpaid = contributions.findUnpaid(tenantId, plan.getId());
                running.add(new SusuDtos.CollectablePlan(plan.getId(), plan.getPlanNumber(), plan.getAccountId(),
                        currencies.present(plan.getContributionAmount(), plan.getCurrency()), plan.getCurrency(),
                        plan.getFrequencyCode(), unpaid.isEmpty() ? null : unpaid.getFirst().getDueDate(),
                        unpaid.stream().filter(c -> c.getStatus() == SusuContribution.Status.MISSED).count(),
                        unpaid.size()));
            }
            result.put(customerId, running);
        }
        return result;
    }

    // ----------------------------------------------------------------------------------- for the module

    /**
     * Schedules the contributions of a cycle (those up to the plan's end date).
     *
     * @return how many were scheduled
     */
    int scheduleCycle(SusuPlan plan, SusuFrequency frequency, int cycle) {
        List<SusuContribution> scheduled = new ArrayList<>();
        int first = plan.firstOfCycle(cycle);
        for (int sequence = first; sequence < first + plan.getCycleLength(); sequence++) {
            LocalDate due = frequency.dueDate(plan.getStartDate(), sequence);
            if (plan.getEndDate() != null && due.isAfter(plan.getEndDate())) {
                break;
            }
            scheduled.add(new SusuContribution(UuidV7.next(), plan.getTenantId(), plan.getId(), sequence, cycle, due,
                    plan.getContributionAmount()));
        }
        contributions.saveAll(scheduled);
        contributions.flush();
        plan.startCycle(cycle);
        return scheduled.size();
    }

    SusuFrequency frequencyOf(SusuPlan plan) {
        return frequencies.findById(new SusuFrequency.Key(plan.getTenantId(), plan.getFrequencyCode()))
                .orElseThrow();
    }

    private SusuPlan loadInScope(UUID planId) {
        BranchScope scope = CurrentActor.require().branchScope();
        return plans.findByTenantIdAndId(TenantContext.requireTenantId(), planId)
                .filter(plan -> scope.permits(plan.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Susu plan"));
    }

    private SusuPlan lockInScope(UUID planId) {
        loadInScope(planId);
        return plans.lockByTenantIdAndId(TenantContext.requireTenantId(), planId).orElseThrow();
    }

    private SusuDtos.PlanDetail detail(SusuPlan plan) {
        List<SusuContribution> all = contributions.findAllByTenantIdAndPlanIdOrderBySequenceNo(plan.getTenantId(),
                plan.getId());
        String currency = plan.getCurrency();
        return new SusuDtos.PlanDetail(summary(plan, all), all.stream()
                .map(c -> new SusuDtos.Contribution(c.getSequenceNo(), c.getCycleNo(), c.getDueDate(),
                        currencies.present(c.getAmount(), currency), c.getStatus().name(), c.getPaidAt(),
                        c.getCollectionId(), c.getFinancialTransactionId(), c.getWaiveReason()))
                .toList(),
                commissions.ofPlan(plan.getTenantId(), plan.getId()).stream()
                        .map(c -> new SusuDtos.Commission(c.cycleNo(), currencies.present(c.amountDue(), currency),
                                currencies.present(c.amountCharged(), currency), c.businessDate()))
                        .toList());
    }

    private SusuDtos.Plan summary(SusuPlan plan, List<SusuContribution> all) {
        String currency = plan.getCurrency();
        long paid = all.stream().filter(c -> c.getStatus() == SusuContribution.Status.PAID).count();
        long missed = all.stream().filter(c -> c.getStatus() == SusuContribution.Status.MISSED).count();
        LocalDate nextDue = all.stream().filter(SusuContribution::isUnpaid).map(SusuContribution::getDueDate)
                .findFirst().orElse(null);
        BigDecimal amount = plan.getContributionAmount();
        return new SusuDtos.Plan(plan.getId(), plan.getPlanNumber(), plan.getCustomerId(), plan.getAccountId(),
                plan.getBranchId(), plan.getFrequencyCode(), currencies.present(amount, currency), currency,
                plan.getCycleLength(), plan.getCommissionContributions(), plan.getStartDate(), plan.getEndDate(),
                plan.getTargetAmount() == null ? null : currencies.present(plan.getTargetAmount(), currency),
                plan.getStatus().name(), plan.getCurrentCycle(), paid, missed,
                currencies.present(amount.multiply(BigDecimal.valueOf(missed)), currency), nextDue,
                currencies.present(amount.multiply(BigDecimal.valueOf(paid)), currency), plan.getCreatedAt(),
                plan.getClosedAt(), plan.getCloseReason(), plan.getVersion());
    }

    private static SusuDtos.Frequency toResponse(SusuFrequency frequency) {
        return new SusuDtos.Frequency(frequency.getCode(), frequency.getName(), frequency.getIntervalUnit().name(),
                frequency.getIntervalCount(), frequency.isActive());
    }
}
