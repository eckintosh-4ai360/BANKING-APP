package com.company.banking.loan.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.References;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.idempotency.IdempotencyService;
import com.company.banking.common.idempotency.IdempotencyService.Result;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.service.CustomerService;
import com.company.banking.ledger.dto.BalanceSnapshot;
import com.company.banking.ledger.dto.OpenLedgerAccountCommand;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.PostingRequest;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.JournalSource;
import com.company.banking.ledger.model.LedgerAccountType;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.ledger.service.ChartOfAccountService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.ledger.service.PostingEngine;
import com.company.banking.loan.dto.LoanDtos;
import com.company.banking.loan.entity.Loan;
import com.company.banking.loan.entity.LoanApplication;
import com.company.banking.loan.entity.LoanInstallment;
import com.company.banking.loan.entity.LoanProduct;
import com.company.banking.loan.entity.LoanProductVersion;
import com.company.banking.loan.entity.LoanRepayment;
import com.company.banking.loan.exception.LoanErrorCode;
import com.company.banking.loan.repository.LoanInstallmentRepository;
import com.company.banking.loan.repository.LoanRepaymentRepository;
import com.company.banking.loan.repository.LoanRepository;
import com.company.banking.loan.schedule.InterestEarned;
import com.company.banking.loan.schedule.RepaymentAllocator;
import com.company.banking.loan.schedule.ScheduleCalculator;
import com.company.banking.transaction.dto.LoanMovementCommand;
import com.company.banking.transaction.dto.MovementResponse;
import com.company.banking.transaction.model.TransactionType;
import com.company.banking.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Disbursed loans: disbursement, repayment, interest accrual and settlement.
 *
 * <p>A loan's balances are three ledger accounts of its own (debit-normal, never below zero): principal, interest
 * receivable and penalty receivable. Interest is recognised as the schedule earns it ({@link InterestEarned}):
 * end-of-day accrues it daily and a repayment first catches up to today, so a repayment never pays interest that
 * was not yet recognised. The schedule records what each repayment settled.
 *
 * <p>Repayments are split by {@link RepaymentAllocator}: due installments oldest first in the product's allocation
 * order, then principal of the next installments. Paying exactly the payoff amount settles the loan (unearned
 * interest waived) and closes it; more than the payoff amount is refused, as is an amount that would repay all
 * principal while leaving interest owing.
 */
@Service
@RequiredArgsConstructor
public class LoanService {

    static final String RESOURCE = "LOAN";
    private static final String OWNER_PRINCIPAL = "LOAN";
    private static final String OWNER_INTEREST = "LOAN_INTEREST";
    private static final String OWNER_PENALTY = "LOAN_PENALTY";

    private final LoanRepository loans;
    private final LoanInstallmentRepository installments;
    private final LoanRepaymentRepository repayments;
    private final LoanApplicationService applicationService;
    private final LoanProductService productService;
    private final LoanTerms loanTerms;
    private final TransactionService transactionService;
    private final LedgerAccountService ledgerAccounts;
    private final ChartOfAccountService chartOfAccounts;
    private final PostingEngine postingEngine;
    private final CurrencyService currencies;
    private final BusinessDateService businessDates;
    private final CustomerService customerService;
    private final IdempotencyService idempotency;
    private final AuditService auditService;
    private final Clock clock;

    private record Request(UUID id, Object body) {
    }

    // ---------------------------------------------------------------------------------------------- disbursement

    /**
     * Disburses an approved application: opens the loan's ledger accounts, credits the borrower's account with the
     * principal less the processing fee, and fixes the schedule. Safe to retry with the same key.
     */
    @Transactional
    public Result<LoanDtos.LoanDetail> disburse(String idempotencyKey, UUID applicationId, LoanDtos.Disburse request) {
        return idempotent("LOAN_DISBURSEMENT", idempotencyKey, new Request(applicationId, request),
                LoanDtos.LoanDetail.class, detail -> detail.loan().id(),
                () -> disbursement(idempotencyKey, applicationId, request));
    }

    private LoanDtos.LoanDetail disbursement(String idempotencyKey, UUID applicationId, LoanDtos.Disburse request) {
        UUID disburser = requireStaff();
        LoanApplication application = applicationService.lockForDisbursement(applicationId, disburser);
        LoanProductVersion version = productService.version(application.getProductVersionId());
        LocalDate today = businessDates.today();
        LocalDate firstDue = request.firstDueDate() != null ? request.firstDueDate()
                : application.getFirstDueDate() != null && application.getFirstDueDate().isAfter(today)
                ? application.getFirstDueDate() : null;
        loanTerms.requireFirstDueDate(version, today, firstDue);
        BigDecimal principal = application.getApprovedAmount();
        int count = application.getApprovedInstallments();
        ScheduleCalculator.Schedule schedule = loanTerms.schedule(version, principal, count, today, firstDue);
        BigDecimal fee = loanTerms.processingFee(version, principal);

        UUID tenantId = application.getTenantId();
        UUID loanId = UuidV7.next();
        String loanNumber = References.next("LN", today);
        String currency = application.getCurrency();
        UUID branchId = application.getBranchId();
        UUID principalAccount = openLedgerAccount(version.getPrincipalGlId(), branchId, currency,
                loanNumber + " principal", OWNER_PRINCIPAL, loanId);
        UUID interestAccount = openLedgerAccount(version.getInterestReceivableGlId(), branchId, currency,
                loanNumber + " interest", OWNER_INTEREST, loanId);
        UUID penaltyAccount = openLedgerAccount(version.getPenaltyReceivableGlId(), branchId, currency,
                loanNumber + " penalties", OWNER_PENALTY, loanId);

        String narration = request.narration() == null || request.narration().isBlank()
                ? "Loan " + loanNumber + " disbursed" : request.narration().trim();
        MovementResponse movement = transactionService.postLoanMovement(new LoanMovementCommand(
                TransactionType.LOAN_DISBURSEMENT, application.getDisbursementAccountId(), currency, principal, fee,
                version.getFeeIncomeGlId(), List.of(PostingLine.toLedgerAccount(principalAccount, EntryDirection.DEBIT,
                principal, "Loan " + loanNumber + " principal")), branchId, narration, loanNumber,
                idempotencyKey));

        Loan loan = loans.saveAndFlush(Loan.builder()
                .id(loanId)
                .tenantId(tenantId)
                .loanNumber(loanNumber)
                .applicationId(applicationId)
                .customerId(application.getCustomerId())
                .branchId(branchId)
                .productVersionId(version.getId())
                .repaymentAccountId(application.getDisbursementAccountId())
                .currency(currency)
                .principal(principal)
                .interestMethod(version.getInterestMethod())
                .annualRate(version.getAnnualRate())
                .dayCount(version.getDayCount())
                .repaymentFrequency(version.getRepaymentFrequency())
                .installments(count)
                .principalGrace(version.getPrincipalGrace())
                .interestGrace(version.getInterestGrace())
                .roundingMode(version.getRoundingMode())
                .allocationOrder(version.getAllocationOrder())
                .penaltyRate(version.getPenaltyRate())
                .penaltyGraceDays(version.getPenaltyGraceDays())
                .processingFee(fee)
                .principalLedgerAccountId(principalAccount)
                .interestLedgerAccountId(interestAccount)
                .penaltyLedgerAccountId(penaltyAccount)
                .interestIncomeGlId(version.getInterestIncomeGlId())
                .penaltyIncomeGlId(version.getPenaltyIncomeGlId())
                .feeIncomeGlId(version.getFeeIncomeGlId())
                .disbursementDate(today)
                .firstDueDate(schedule.installments().getFirst().dueDate())
                .maturityDate(schedule.maturityDate())
                .disbursementTransactionId(movement.transaction().id())
                .disbursedBy(disburser)
                .disbursedAt(clock.instant())
                .build());
        installments.saveAllAndFlush(schedule.installments().stream()
                .map(line -> new LoanInstallment(loanId, 1, line.number(), tenantId, line.fromDate(), line.dueDate(),
                        line.principal(), line.interest()))
                .toList());
        applicationService.markDisbursed(application, disburser, narration);

        LoanDtos.LoanDetail detail = detail(loan);
        auditService.record(AuditEvent.builder("LOAN_DISBURSED", RESOURCE)
                .resourceId(loanId)
                .resourceReference(loanNumber)
                .branchId(branchId)
                .metadata("applicationId", applicationId)
                .metadata("transactionId", movement.transaction().id())
                .after(detail.loan())
                .build());
        return detail;
    }

    private UUID openLedgerAccount(UUID glId, UUID branchId, String currency, String name, String ownerType,
                                   UUID loanId) {
        return ledgerAccounts.open(new OpenLedgerAccountCommand(glId, null, branchId, currency, LedgerAccountType.LOAN,
                true, name, ownerType, loanId)).id();
    }

    // ------------------------------------------------------------------------------------------------ repayment

    /**
     * Takes a repayment from the borrower's account or in cash at the caller's till. Safe to retry with the same key.
     */
    @Transactional
    public Result<LoanDtos.RepaymentReceipt> repay(String idempotencyKey, UUID loanId, LoanDtos.Repay request) {
        return idempotent("LOAN_REPAYMENT", idempotencyKey, new Request(loanId, request),
                LoanDtos.RepaymentReceipt.class, receipt -> receipt.repayment().id(),
                () -> repayment(idempotencyKey, loanId, request));
    }

    private LoanDtos.RepaymentReceipt repayment(String idempotencyKey, UUID loanId, LoanDtos.Repay request) {
        Loan loan = lockInScope(loanId);
        if (!loan.isActive()) {
            throw new BankingException(LoanErrorCode.LOAN_NOT_ACTIVE);
        }
        String currency = loan.getCurrency();
        BigDecimal amount = request.amount();
        currencies.requireValidAmount(amount, currency);
        LocalDate today = businessDates.today();
        List<LoanInstallment> schedule = schedule(loan);
        accrueThrough(loan, schedule, today);

        RepaymentAllocator.Result payoff = payoffOf(loan, schedule, today);
        int comparison = amount.compareTo(payoff.total());
        if (comparison > 0) {
            throw new BankingException(LoanErrorCode.OVERPAYMENT, "The payoff amount today is " + currency + " "
                    + currencies.present(payoff.total(), currency).toPlainString() + ".");
        }
        boolean settles = comparison == 0;
        RepaymentAllocator.Result split = settles ? payoff : RepaymentAllocator.allocate(owed(schedule), today, amount,
                RepaymentAllocator.parseOrder(loan.getAllocationOrder()));
        if (!settles && (split.unallocated().signum() > 0 || split.principal().compareTo(payoff.principal()) == 0)) {
            throw new BankingException(LoanErrorCode.SETTLE_WITH_PAYOFF, "Pay the payoff amount of " + currency + " "
                    + currencies.present(payoff.total(), currency).toPlainString() + " to settle the loan.");
        }

        String narration = request.narration() == null || request.narration().isBlank()
                ? "Loan " + loan.getLoanNumber() + " repayment" : request.narration().trim();
        boolean cash = "CASH".equals(request.source());
        MovementResponse movement = transactionService.postLoanMovement(new LoanMovementCommand(
                TransactionType.LOAN_REPAYMENT, cash ? null : loan.getRepaymentAccountId(), currency, amount, null, null,
                repaymentLines(loan, split), loan.getBranchId(), narration, loan.getLoanNumber(), idempotencyKey));

        for (int index = 0; index < schedule.size(); index++) {
            RepaymentAllocator.Allocation allocation = split.allocations().get(index);
            LoanInstallment installment = schedule.get(index);
            if (allocation.principal().signum() > 0 || allocation.interest().signum() > 0
                    || allocation.penalty().signum() > 0) {
                installment.pay(allocation.principal(), allocation.interest(), allocation.penalty(), today);
            }
            if (allocation.interestWaived().signum() > 0) {
                installment.waiveInterest(allocation.interestWaived(), today);
            }
        }
        installments.saveAllAndFlush(schedule);
        LoanRepayment repayment = repayments.saveAndFlush(LoanRepayment.builder()
                .id(UuidV7.next())
                .tenantId(loan.getTenantId())
                .loanId(loanId)
                .financialTransactionId(movement.transaction().id())
                .source(cash ? "CASH" : "ACCOUNT")
                .amount(amount)
                .penaltyAllocated(split.penalty())
                .feeAllocated(BigDecimal.ZERO)
                .interestAllocated(split.interest())
                .principalAllocated(split.principal())
                .businessDate(today)
                .receivedBy(CurrentActor.currentActorId().orElse(null))
                .createdAt(clock.instant())
                .build());
        if (settles) {
            close(loan, today);
        }
        loans.saveAndFlush(loan);

        LoanDtos.Repayment receipt = toResponse(repayment, currency);
        auditService.record(AuditEvent.builder("LOAN_REPAYMENT_RECEIVED", RESOURCE)
                .resourceId(loanId)
                .resourceReference(loan.getLoanNumber())
                .branchId(loan.getBranchId())
                .metadata("settled", settles)
                .after(receipt)
                .build());
        return new LoanDtos.RepaymentReceipt(receipt, summary(loan), settles);
    }

    /**
     * Credits what the repayment pays to the loan's accounts. Interest collected on a non-accrual loan was held in
     * suspense, not income: collecting it recognises it.
     */
    private List<PostingLine> repaymentLines(Loan loan, RepaymentAllocator.Result split) {
        String narration = "Loan " + loan.getLoanNumber();
        List<PostingLine> lines = new ArrayList<>();
        if (split.principal().signum() > 0) {
            lines.add(PostingLine.toLedgerAccount(loan.getPrincipalLedgerAccountId(), EntryDirection.CREDIT,
                    split.principal(), narration + " principal"));
        }
        if (split.interest().signum() > 0) {
            lines.add(PostingLine.toLedgerAccount(loan.getInterestLedgerAccountId(), EntryDirection.CREDIT,
                    split.interest(), narration + " interest"));
            if (loan.isNonAccrual()) {
                lines.add(PostingLine.toGl(suspenseGl(), loan.getBranchId(), loan.getCurrency(), EntryDirection.DEBIT,
                        split.interest(), narration + " interest collected"));
                lines.add(PostingLine.toGl(loan.getInterestIncomeGlId(), loan.getBranchId(), loan.getCurrency(),
                        EntryDirection.CREDIT, split.interest(), narration + " interest collected"));
            }
        }
        if (split.penalty().signum() > 0) {
            lines.add(PostingLine.toLedgerAccount(loan.getPenaltyLedgerAccountId(), EntryDirection.CREDIT,
                    split.penalty(), narration + " penalties"));
        }
        return lines;
    }

    /**
     * Closes a settled loan after checking its accounts are all at zero (anything else is a bug, so the whole
     * repayment rolls back), and hands back its collateral.
     */
    private void close(Loan loan, LocalDate today) {
        Map<UUID, BalanceSnapshot> balances = ledgerAccounts.balances(List.of(loan.getPrincipalLedgerAccountId(),
                loan.getInterestLedgerAccountId(), loan.getPenaltyLedgerAccountId()));
        if (balances.values().stream().anyMatch(balance -> balance.ledgerBalance().signum() != 0)) {
            throw new IllegalStateException("Loan " + loan.getLoanNumber() + " settled with balances left: "
                    + balances.values());
        }
        loan.close(Loan.Status.CLOSED, today);
        applicationService.releaseAllCollateral(loan.getApplicationId());
        auditService.record(AuditEvent.builder("LOAN_CLOSED", RESOURCE)
                .resourceId(loan.getId())
                .resourceReference(loan.getLoanNumber())
                .branchId(loan.getBranchId())
                .metadata("closedOn", today.toString())
                .build());
    }

    // -------------------------------------------------------------------------------------------------- accrual

    /**
     * Recognises the interest the schedule has earned by {@code date} and not yet recognised: Dr interest
     * receivable, Cr interest income (interest in suspense for a non-accrual loan). Recognition is cumulative, so it
     * never drifts and running it twice changes nothing.
     *
     * @return the amount recognised now
     */
    @Transactional(propagation = Propagation.MANDATORY)
    BigDecimal accrueThrough(Loan loan, List<LoanInstallment> schedule, LocalDate date) {
        BigDecimal earned = earnedThrough(loan, schedule, date);
        BigDecimal due = earned.subtract(loan.getInterestRecognised());
        if (due.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        String narration = "Loan " + loan.getLoanNumber() + " interest to " + date;
        postingEngine.post(new PostingRequest(JournalSource.LOAN, loan.getLoanNumber(), null, loan.getBranchId(), null,
                narration, null, List.of(
                PostingLine.toLedgerAccount(loan.getInterestLedgerAccountId(), EntryDirection.DEBIT, due, narration),
                PostingLine.toGl(loan.isNonAccrual() ? suspenseGl() : loan.getInterestIncomeGlId(),
                        loan.getBranchId(), loan.getCurrency(), EntryDirection.CREDIT, due, narration))));
        loan.recordAccrual(earned, date);
        loans.saveAndFlush(loan);
        return due;
    }

    private BigDecimal earnedThrough(Loan loan, List<LoanInstallment> schedule, LocalDate date) {
        return InterestEarned.through(schedule.stream()
                        .map(installment -> new InterestEarned.Period(installment.getFromDate(),
                                installment.getDueDate(), installment.getInterestDue()))
                        .toList(), loan.getDisbursementDate(), date,
                currencies.require(loan.getCurrency()).minorUnits());
    }

    private UUID suspenseGl() {
        return chartOfAccounts.requireSystem(SystemAccount.INTEREST_IN_SUSPENSE).id();
    }

    // ---------------------------------------------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public LoanDtos.LoanDetail get(UUID loanId) {
        return detail(loadInScope(loanId));
    }

    @Transactional(readOnly = true)
    public LoanDtos.Payoff payoff(UUID loanId) {
        Loan loan = loadInScope(loanId);
        if (!loan.isActive()) {
            throw new BankingException(LoanErrorCode.LOAN_NOT_ACTIVE);
        }
        LocalDate today = businessDates.today();
        return toPayoff(payoffOf(loan, schedule(loan), today), today, loan.getCurrency());
    }

    @Transactional(readOnly = true)
    public PageResponse<LoanDtos.Loan> search(UUID customerId, String status, PageRequest page) {
        BranchScope scope = CurrentActor.require().branchScope();
        Collection<UUID> branches = scope.allBranches() || scope.branchIds().isEmpty()
                ? Set.of(new UUID(0, 0)) : scope.branchIds();
        Page<Loan> found = loans.search(TenantContext.requireTenantId(), scope.allBranches(), branches, customerId,
                status == null ? null : Loan.Status.valueOf(status), page);
        Map<UUID, LoanDtos.Loan> summaries = summaries(found.getContent()).stream()
                .collect(Collectors.toMap(LoanDtos.Loan::id, Function.identity()));
        return PageResponse.from(found, loan -> summaries.get(loan.getId()));
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private RepaymentAllocator.Result payoffOf(Loan loan, List<LoanInstallment> schedule, LocalDate today) {
        return RepaymentAllocator.settle(owed(schedule), schedule.stream()
                        .map(installment -> installment.getInterestPaid().add(installment.getInterestWaived()))
                        .toList(), today, earnedThrough(loan, schedule, today));
    }

    private static List<RepaymentAllocator.Owed> owed(List<LoanInstallment> schedule) {
        return schedule.stream()
                .map(installment -> new RepaymentAllocator.Owed(installment.getNumber(), installment.getDueDate(),
                        installment.principalOutstanding(), installment.interestOutstanding(),
                        installment.penaltyOutstanding()))
                .toList();
    }

    private List<LoanInstallment> schedule(Loan loan) {
        return installments.findSchedule(loan.getTenantId(), loan.getId(), loan.getScheduleVersion());
    }

    private <T> Result<T> idempotent(String operation, String key, Request request, Class<T> responseType,
                                     Function<T, UUID> resourceId, Supplier<T> action) {
        IdempotencyService.requireValidKey(key);
        AuthenticatedActor actor = CurrentActor.require();
        String scope = actor.type() + ":" + (actor.id() == null ? "SYSTEM" : actor.id()) + ":" + operation;
        return idempotency.execute(new IdempotencyService.Request(scope, key, idempotency.hash(request), RESOURCE),
                responseType, resourceId, action);
    }

    private static UUID requireStaff() {
        return CurrentActor.currentActorId().orElseThrow(() -> new BankingException(CommonErrorCode.ACCESS_DENIED));
    }

    private Loan loadInScope(UUID loanId) {
        BranchScope scope = CurrentActor.require().branchScope();
        return loans.findByTenantIdAndId(TenantContext.requireTenantId(), loanId)
                .filter(loan -> scope.permits(loan.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Loan"));
    }

    private Loan lockInScope(UUID loanId) {
        loadInScope(loanId);
        return loans.lockByTenantIdAndId(TenantContext.requireTenantId(), loanId).orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------ responses

    private LoanDtos.LoanDetail detail(Loan loan) {
        String currency = loan.getCurrency();
        LocalDate today = businessDates.today();
        List<LoanInstallment> schedule = schedule(loan);
        return new LoanDtos.LoanDetail(summaries(List.of(loan)).getFirst(),
                schedule.stream().map(installment -> toResponse(installment, today, currency)).toList(),
                repayments.findAllByTenantIdAndLoanIdOrderByCreatedAtDesc(loan.getTenantId(), loan.getId()).stream()
                        .map(repayment -> toResponse(repayment, currency)).toList(),
                loan.isActive() ? toPayoff(payoffOf(loan, schedule, today), today, currency) : null);
    }

    private LoanDtos.Loan summary(Loan loan) {
        return summaries(List.of(loan)).getFirst();
    }

    private List<LoanDtos.Loan> summaries(List<Loan> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        LocalDate today = businessDates.today();
        Map<UUID, CustomerSummary> customers = customerService.summaries(page.stream().map(Loan::getCustomerId)
                .collect(Collectors.toSet()));
        Map<UUID, BalanceSnapshot> balances = ledgerAccounts.balances(page.stream()
                .flatMap(loan -> Stream.of(loan.getPrincipalLedgerAccountId(),
                        loan.getInterestLedgerAccountId(), loan.getPenaltyLedgerAccountId()))
                .toList());
        Map<UUID, LoanProductVersion> versions = page.stream().map(Loan::getProductVersionId).distinct()
                .collect(Collectors.toMap(Function.identity(), productService::version));
        Map<UUID, LoanProduct> products = productService.productsById(versions.values().stream()
                .map(LoanProductVersion::getProductId).collect(Collectors.toSet()));
        return page.stream().map(loan -> {
            String currency = loan.getCurrency();
            List<LoanInstallment> schedule = schedule(loan);
            BigDecimal arrears = BigDecimal.ZERO;
            LoanInstallment next = null;
            for (LoanInstallment installment : schedule) {
                BigDecimal outstanding = outstanding(installment);
                if (installment.getDueDate().isBefore(today)) {
                    arrears = arrears.add(outstanding);
                } else if (next == null && outstanding.signum() > 0) {
                    next = installment;
                }
            }
            CustomerSummary customer = customers.get(loan.getCustomerId());
            LoanProduct product = products.get(versions.get(loan.getProductVersionId()).getProductId());
            return new LoanDtos.Loan(loan.getId(), loan.getLoanNumber(), loan.getApplicationId(), loan.getCustomerId(),
                    customer == null ? null : customer.displayName(), loan.getBranchId(), loan.getProductVersionId(),
                    product == null ? null : product.getCode(), product == null ? null : product.getName(),
                    loan.getRepaymentAccountId(), currency, present(loan.getPrincipal(), currency),
                    loan.getInterestMethod().name(), loan.getAnnualRate().stripTrailingZeros(),
                    loan.getDayCount().name(), loan.getRepaymentFrequency().name(), loan.getInstallments(),
                    present(loan.getProcessingFee(), currency), loan.getDisbursementDate(), loan.getFirstDueDate(),
                    loan.getMaturityDate(), loan.getStatus().name(), loan.getDaysPastDue(), loan.getDelinquencyBand(),
                    loan.isNonAccrual(), balance(balances, loan.getPrincipalLedgerAccountId(), currency),
                    balance(balances, loan.getInterestLedgerAccountId(), currency),
                    balance(balances, loan.getPenaltyLedgerAccountId(), currency), present(arrears, currency),
                    next == null ? null : next.getDueDate(),
                    next == null ? null : present(outstanding(next), currency), loan.getClosedOn(), loan.getVersion());
        }).toList();
    }

    private static BigDecimal outstanding(LoanInstallment installment) {
        return installment.principalOutstanding().add(installment.interestOutstanding())
                .add(installment.penaltyOutstanding());
    }

    private BigDecimal balance(Map<UUID, BalanceSnapshot> balances, UUID ledgerAccountId, String currency) {
        BalanceSnapshot balance = balances.get(ledgerAccountId);
        return present(balance == null ? BigDecimal.ZERO : balance.ledgerBalance(), currency);
    }

    private LoanDtos.Installment toResponse(LoanInstallment installment, LocalDate today, String currency) {
        BigDecimal outstanding = outstanding(installment);
        boolean anythingPaid = installment.getPrincipalPaid().signum() > 0 || installment.getInterestPaid().signum() > 0;
        String status;
        if (installment.isSettled()) {
            status = "PAID";
        } else if (installment.getDueDate().isBefore(today)) {
            status = "OVERDUE";
        } else if (installment.getDueDate().isEqual(today)) {
            status = "DUE";
        } else {
            status = anythingPaid ? "PARTLY_PAID" : "UPCOMING";
        }
        return new LoanDtos.Installment(installment.getNumber(), installment.getFromDate(), installment.getDueDate(),
                present(installment.getPrincipalDue(), currency), present(installment.getInterestDue(), currency),
                present(installment.getPenaltyDue(), currency), present(installment.getPrincipalPaid(), currency),
                present(installment.getInterestPaid(), currency), present(installment.getPenaltyPaid(), currency),
                present(installment.getInterestWaived(), currency), present(outstanding, currency),
                installment.getPaidOn(), status);
    }

    private LoanDtos.Repayment toResponse(LoanRepayment repayment, String currency) {
        return new LoanDtos.Repayment(repayment.getId(), repayment.getFinancialTransactionId(), repayment.getSource(),
                present(repayment.getAmount(), currency), present(repayment.getPenaltyAllocated(), currency),
                present(repayment.getFeeAllocated(), currency), present(repayment.getInterestAllocated(), currency),
                present(repayment.getPrincipalAllocated(), currency), repayment.getBusinessDate(),
                repayment.getReceivedBy(), repayment.getCreatedAt());
    }

    private LoanDtos.Payoff toPayoff(RepaymentAllocator.Result payoff, LocalDate today, String currency) {
        return new LoanDtos.Payoff(today, present(payoff.principal(), currency), present(payoff.interest(), currency),
                present(payoff.penalty(), currency), present(payoff.total(), currency),
                present(payoff.interestWaived(), currency));
    }

    private BigDecimal present(BigDecimal amount, String currency) {
        return currencies.present(amount, currency);
    }
}
