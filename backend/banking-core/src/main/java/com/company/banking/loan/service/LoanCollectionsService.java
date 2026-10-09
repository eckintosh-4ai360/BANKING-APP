package com.company.banking.loan.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.service.CustomerService;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.loan.dto.LoanDtos;
import com.company.banking.loan.entity.Loan;
import com.company.banking.loan.entity.LoanCollectionActivity;
import com.company.banking.loan.entity.LoanInstallment;
import com.company.banking.loan.exception.LoanErrorCode;
import com.company.banking.loan.repository.LoanCollectionActivityRepository;
import com.company.banking.loan.repository.LoanInstallmentRepository;
import com.company.banking.loan.repository.LoanRepaymentRepository;
import com.company.banking.loan.repository.LoanRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Collections: the queue of loans in arrears, and the calls, visits and promises to pay recorded against a loan. A
 * promise is kept when repayments from the day it was made to its date add up to the amount promised; end-of-day
 * decides on the promised date.
 */
@Service
@RequiredArgsConstructor
public class LoanCollectionsService {

    private static final int MAX_PROMISE_DAYS = 90;

    private final LoanRepository loans;
    private final LoanInstallmentRepository installments;
    private final LoanRepaymentRepository repayments;
    private final LoanCollectionActivityRepository activities;
    private final CustomerService customerService;
    private final CurrencyService currencies;
    private final BusinessDateService businessDates;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public LoanDtos.CollectionActivity record(UUID loanId, LoanDtos.NewActivity request) {
        Loan loan = loadInScope(loanId);
        if (loan.getStatus() == Loan.Status.CLOSED) {
            throw new BankingException(LoanErrorCode.LOAN_NOT_ACTIVE);
        }
        LoanCollectionActivity.Type type = LoanCollectionActivity.Type.valueOf(request.type());
        LocalDate today = businessDates.today();
        boolean promise = type == LoanCollectionActivity.Type.PROMISE;
        if (promise != (request.promisedAmount() != null) || promise != (request.promisedDate() != null)) {
            throw new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION,
                    "A promise to pay has an amount and a date; other activities have neither.");
        }
        if (promise) {
            currencies.requireValidAmount(request.promisedAmount(), loan.getCurrency());
            if (request.promisedDate().isBefore(today) || request.promisedDate().isAfter(today.plusDays(
                    MAX_PROMISE_DAYS))) {
                throw new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION,
                        "A promise to pay is for today or a date in the next " + MAX_PROMISE_DAYS + " days.");
            }
        }
        LoanCollectionActivity activity = activities.saveAndFlush(new LoanCollectionActivity(UuidV7.next(),
                loan.getTenantId(), loanId, type, request.note().trim(), loan.getDaysPastDue(),
                request.promisedAmount(), request.promisedDate(), today,
                CurrentActor.currentActorId().orElseThrow(() -> new BankingException(CommonErrorCode.ACCESS_DENIED)),
                clock.instant()));
        LoanDtos.CollectionActivity response = toResponse(activity, loan.getCurrency());
        auditService.record(AuditEvent.builder("LOAN_COLLECTION_ACTIVITY_RECORDED", LoanService.RESOURCE)
                .resourceId(loanId)
                .resourceReference(loan.getLoanNumber())
                .branchId(loan.getBranchId())
                .after(response)
                .build());
        return response;
    }

    @Transactional(readOnly = true)
    public List<LoanDtos.CollectionActivity> activities(UUID loanId) {
        Loan loan = loadInScope(loanId);
        return activities.findAllByTenantIdAndLoanIdOrderByCreatedAtDesc(loan.getTenantId(), loanId).stream()
                .map(activity -> toResponse(activity, loan.getCurrency()))
                .toList();
    }

    /**
     * Active loans at least {@code minDays} past due in the caller's branches, the longest overdue first, with what
     * is overdue and the latest collection activity.
     */
    @Transactional(readOnly = true)
    public PageResponse<LoanDtos.ArrearsItem> queue(int minDays, PageRequest page) {
        UUID tenantId = TenantContext.requireTenantId();
        BranchScope scope = CurrentActor.require().branchScope();
        Collection<UUID> branches = scope.allBranches() || scope.branchIds().isEmpty()
                ? Set.of(new UUID(0, 0)) : scope.branchIds();
        Page<Loan> found = loans.inArrears(tenantId, scope.allBranches(), branches, Math.max(1, minDays), page);
        List<UUID> ids = found.getContent().stream().map(Loan::getId).toList();
        Map<UUID, CustomerSummary> customers = customerService.summaries(found.getContent().stream()
                .map(Loan::getCustomerId).collect(Collectors.toSet()));
        Map<UUID, LoanCollectionActivity> latest = ids.isEmpty() ? Map.of() : activities.findLatest(tenantId, ids)
                .stream().collect(Collectors.toMap(LoanCollectionActivity::getLoanId, Function.identity(),
                        (first, second) -> first));
        LocalDate today = businessDates.today();
        return PageResponse.from(found, loan -> {
            String currency = loan.getCurrency();
            BigDecimal overdue = installments.findSchedule(tenantId, loan.getId(), loan.getScheduleVersion()).stream()
                    .filter(installment -> installment.getDueDate().isBefore(today))
                    .map(LoanCollectionsService::outstanding)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            CustomerSummary customer = customers.get(loan.getCustomerId());
            LoanCollectionActivity last = latest.get(loan.getId());
            return new LoanDtos.ArrearsItem(loan.getId(), loan.getLoanNumber(), loan.getCustomerId(),
                    customer == null ? null : customer.displayName(),
                    customer == null ? null : customer.primaryPhone(), loan.getBranchId(), currency,
                    loan.getDaysPastDue(), loan.getDelinquencyBand(), currencies.present(overdue, currency),
                    last == null ? null : toResponse(last, currency));
        });
    }

    /**
     * Decides the loan's open promises whose date has come: kept when repayments since the promise was made add up
     * to the amount promised. Idempotent.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    int settlePromisesDue(UUID loanId, LocalDate closed) {
        UUID tenantId = TenantContext.requireTenantId();
        List<LoanCollectionActivity> due = activities.findPromisesDueBy(tenantId, loanId, closed);
        for (LoanCollectionActivity promise : due) {
            BigDecimal paid = repayments.sumBetween(tenantId, loanId, promise.getBusinessDate(),
                    promise.getPromisedDate());
            promise.settlePromise(paid.compareTo(promise.getPromisedAmount()) >= 0);
        }
        activities.saveAllAndFlush(due);
        return due.size();
    }

    private Loan loadInScope(UUID loanId) {
        BranchScope scope = CurrentActor.require().branchScope();
        return loans.findByTenantIdAndId(TenantContext.requireTenantId(), loanId)
                .filter(loan -> scope.permits(loan.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Loan"));
    }

    private static BigDecimal outstanding(LoanInstallment installment) {
        return installment.principalOutstanding().add(installment.interestOutstanding())
                .add(installment.penaltyOutstanding());
    }

    private LoanDtos.CollectionActivity toResponse(LoanCollectionActivity activity, String currency) {
        return new LoanDtos.CollectionActivity(activity.getId(), activity.getActivityType().name(),
                activity.getNote(), activity.getDaysPastDue(),
                activity.getPromisedAmount() == null ? null : currencies.present(activity.getPromisedAmount(),
                        currency), activity.getPromisedDate(),
                activity.getPromiseStatus() == null ? null : activity.getPromiseStatus().name(),
                activity.getBusinessDate(), activity.getCreatedBy(), activity.getCreatedAt());
    }
}
