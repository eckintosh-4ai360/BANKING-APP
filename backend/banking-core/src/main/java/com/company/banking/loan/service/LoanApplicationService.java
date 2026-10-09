package com.company.banking.loan.service;

import com.company.banking.account.dto.AccountSummary;
import com.company.banking.account.service.AccountService;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.References;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.service.CustomerService;
import com.company.banking.kyc.service.KycTierService;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.loan.dto.LoanDtos;
import com.company.banking.loan.entity.Loan;
import com.company.banking.loan.entity.LoanApplication;
import com.company.banking.loan.entity.LoanApplication.Status;
import com.company.banking.loan.entity.LoanApplicationStep;
import com.company.banking.loan.entity.LoanCollateral;
import com.company.banking.loan.entity.LoanGuarantor;
import com.company.banking.loan.entity.LoanProduct;
import com.company.banking.loan.entity.LoanProductVersion;
import com.company.banking.loan.exception.LoanErrorCode;
import com.company.banking.loan.repository.LoanApplicationRepository;
import com.company.banking.loan.repository.LoanApplicationStepRepository;
import com.company.banking.loan.repository.LoanCollateralRepository;
import com.company.banking.loan.repository.LoanGuarantorRepository;
import com.company.banking.loan.repository.LoanRepository;
import com.company.banking.staff.service.StaffService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
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
 * Loan applications and their workflow: DRAFT → SUBMITTED → ASSESSED → RECOMMENDED → APPROVED (a second approval
 * above the product's threshold) → DISBURSED ({@link LoanService}), or REJECTED / WITHDRAWN on the way.
 *
 * <p>Separation of duties: whoever recommends, approves, approves a second time or disburses must be someone other
 * than the loan officer and than everyone who took another of those steps (checked here, enforced by the database).
 * Guarantors and collateral count only once verified, by someone other than the loan officer.
 */
@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    static final String RESOURCE = "LOAN_APPLICATION";
    private static final Set<LoanApplicationStep.Type> DECISIONS = EnumSet.of(LoanApplicationStep.Type.RECOMMEND,
            LoanApplicationStep.Type.APPROVE, LoanApplicationStep.Type.SECOND_APPROVE,
            LoanApplicationStep.Type.DISBURSE);
    private static final Set<Status> UNDECIDED = EnumSet.of(Status.DRAFT, Status.SUBMITTED, Status.ASSESSED,
            Status.RECOMMENDED);
    private static final Set<String> DISBURSABLE_ACCOUNTS = Set.of("SAVINGS", "CURRENT");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final LoanApplicationRepository applications;
    private final LoanApplicationStepRepository steps;
    private final LoanGuarantorRepository guarantors;
    private final LoanCollateralRepository collaterals;
    private final LoanRepository loans;
    private final LoanProductService productService;
    private final LoanTerms loanTerms;
    private final CustomerService customerService;
    private final AccountService accountService;
    private final KycTierService kycTiers;
    private final StaffService staffService;
    private final CurrencyService currencies;
    private final BusinessDateService businessDates;
    private final AuditService auditService;
    private final Clock clock;

    // ------------------------------------------------------------------------------------------- applications

    /**
     * Starts an application on the product's published terms, with the caller as loan officer.
     */
    @Transactional
    public LoanDtos.ApplicationDetail create(LoanDtos.NewApplication request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID officer = requireStaff();
        CustomerSummary customer = customerService.getSummary(request.customerId());
        if (!CurrentActor.require().branchScope().permits(customer.homeBranchId())) {
            throw new ResourceNotFoundException("Customer");
        }
        LoanProductVersion version = productService.publishedVersion(request.productId());
        requireEligible(version, customer);
        loanTerms.requireWithinLimits(version, request.requestedAmount(), request.requestedInstallments());
        requireDisbursementAccount(customer.id(), request.disbursementAccountId(), version.getCurrency());
        String currency = version.getCurrency();
        LoanApplication application = applications.saveAndFlush(LoanApplication.builder()
                .id(UuidV7.next())
                .tenantId(tenantId)
                .applicationNumber(References.next("LAP", businessDates.today()))
                .customerId(customer.id())
                .branchId(customer.homeBranchId())
                .productId(version.getProductId())
                .productVersionId(version.getId())
                .currency(currency)
                .requestedAmount(request.requestedAmount())
                .requestedInstallments(request.requestedInstallments())
                .purpose(request.purpose().trim())
                .monthlyIncome(optionalMoney(request.monthlyIncome(), currency))
                .monthlyExpenses(optionalMoney(request.monthlyExpenses(), currency))
                .existingDebt(optionalMoney(request.existingDebt(), currency))
                .disbursementAccountId(request.disbursementAccountId())
                .loanOfficerId(officer)
                .createdAt(clock.instant())
                .build());
        LoanDtos.ApplicationDetail detail = detail(application);
        auditService.record(AuditEvent.builder("LOAN_APPLICATION_CREATED", RESOURCE)
                .resourceId(application.getId())
                .resourceReference(application.getApplicationNumber())
                .branchId(application.getBranchId())
                .after(detail.application())
                .build());
        return detail;
    }

    @Transactional
    public LoanDtos.ApplicationDetail edit(UUID applicationId, LoanDtos.EditApplication request) {
        LoanApplication application = lockInScope(applicationId);
        ConcurrentModificationException.assertVersion(request.version(), application.getVersion());
        requireStatus(application, Status.DRAFT);
        LoanProductVersion version = productService.version(application.getProductVersionId());
        loanTerms.requireWithinLimits(version, request.requestedAmount(), request.requestedInstallments());
        requireDisbursementAccount(application.getCustomerId(), request.disbursementAccountId(),
                application.getCurrency());
        LoanDtos.Application before = detail(application).application();
        String currency = application.getCurrency();
        application.edit(request.requestedAmount(), request.requestedInstallments(), request.purpose().trim(),
                optionalMoney(request.monthlyIncome(), currency), optionalMoney(request.monthlyExpenses(), currency),
                optionalMoney(request.existingDebt(), currency), request.disbursementAccountId(), clock.instant());
        applications.saveAndFlush(application);
        LoanDtos.ApplicationDetail detail = detail(application);
        auditService.record(AuditEvent.builder("LOAN_APPLICATION_EDITED", RESOURCE)
                .resourceId(applicationId)
                .resourceReference(application.getApplicationNumber())
                .branchId(application.getBranchId())
                .before(before)
                .after(detail.application())
                .build());
        return detail;
    }

    @Transactional
    public LoanDtos.ApplicationDetail submit(UUID applicationId, LoanDtos.Step request) {
        LoanApplication application = lockInScope(applicationId);
        ConcurrentModificationException.assertVersion(request.version(), application.getVersion());
        requireStatus(application, Status.DRAFT);
        requireEligible(productService.version(application.getProductVersionId()),
                customerService.getSummary(application.getCustomerId()));
        return advance(application, Status.SUBMITTED, LoanApplicationStep.Type.SUBMIT, request.note(),
                "LOAN_APPLICATION_SUBMITTED");
    }

    /**
     * Records the credit assessment (may be repeated until the application is recommended).
     */
    @Transactional
    public LoanDtos.ApplicationDetail assess(UUID applicationId, LoanDtos.Assess request) {
        LoanApplication application = lockInScope(applicationId);
        ConcurrentModificationException.assertVersion(request.version(), application.getVersion());
        requireStatus(application, Status.SUBMITTED, Status.ASSESSED);
        application.assess(request.riskRating(), request.note().trim(), clock.instant());
        return advance(application, Status.ASSESSED, LoanApplicationStep.Type.ASSESS, request.note(),
                "LOAN_APPLICATION_ASSESSED");
    }

    @Transactional
    public LoanDtos.ApplicationDetail recommend(UUID applicationId, LoanDtos.Step request) {
        LoanApplication application = lockInScope(applicationId);
        ConcurrentModificationException.assertVersion(request.version(), application.getVersion());
        requireStatus(application, Status.ASSESSED);
        requireSeparateActor(application, requireStaff());
        return advance(application, Status.RECOMMENDED, LoanApplicationStep.Type.RECOMMEND, request.note(),
                "LOAN_APPLICATION_RECOMMENDED");
    }

    /**
     * Approves the loan on the given terms (at most what was requested, within the product's limits, with the
     * product's guarantors and collateral verified). Above the product's threshold it then waits for a second
     * approver.
     */
    @Transactional
    public LoanDtos.ApplicationDetail approve(UUID applicationId, LoanDtos.Approve request) {
        LoanApplication application = lockInScope(applicationId);
        ConcurrentModificationException.assertVersion(request.version(), application.getVersion());
        requireStatus(application, Status.RECOMMENDED);
        if (application.getApprovedAmount() != null) {
            throw new BankingException(LoanErrorCode.INVALID_STEP, "The application is waiting for a second approval.");
        }
        requireSeparateActor(application, requireStaff());
        LoanProductVersion version = productService.version(application.getProductVersionId());
        BigDecimal amount = request.approvedAmount();
        loanTerms.requireWithinLimits(version, amount, request.approvedInstallments());
        if (amount.compareTo(application.getRequestedAmount()) > 0) {
            throw new BankingException(LoanErrorCode.OUTSIDE_PRODUCT_LIMITS,
                    "The approved amount cannot exceed the amount applied for.");
        }
        loanTerms.processingFee(version, amount);
        loanTerms.requireFirstDueDate(version, businessDates.today(), request.firstDueDate());
        requireEligible(version, customerService.getSummary(application.getCustomerId()));
        requireSecurity(application, version, amount);
        application.approveTerms(amount, request.approvedInstallments(), request.firstDueDate(), clock.instant());
        boolean second = secondApprovalRequired(version, amount);
        return advance(application, second ? Status.RECOMMENDED : Status.APPROVED, LoanApplicationStep.Type.APPROVE,
                request.note(), "LOAN_APPLICATION_APPROVED");
    }

    @Transactional
    public LoanDtos.ApplicationDetail secondApprove(UUID applicationId, LoanDtos.Step request) {
        LoanApplication application = lockInScope(applicationId);
        ConcurrentModificationException.assertVersion(request.version(), application.getVersion());
        requireStatus(application, Status.RECOMMENDED);
        LoanProductVersion version = productService.version(application.getProductVersionId());
        if (application.getApprovedAmount() == null
                || !secondApprovalRequired(version, application.getApprovedAmount())) {
            throw new BankingException(LoanErrorCode.INVALID_STEP, "The application is not waiting for a second"
                    + " approval.");
        }
        requireSeparateActor(application, requireStaff());
        requireSecurity(application, version, application.getApprovedAmount());
        return advance(application, Status.APPROVED, LoanApplicationStep.Type.SECOND_APPROVE, request.note(),
                "LOAN_APPLICATION_SECOND_APPROVED");
    }

    @Transactional
    public LoanDtos.ApplicationDetail reject(UUID applicationId, LoanDtos.Reject request) {
        LoanApplication application = lockInScope(applicationId);
        ConcurrentModificationException.assertVersion(request.version(), application.getVersion());
        requireStatus(application, Status.SUBMITTED, Status.ASSESSED, Status.RECOMMENDED, Status.APPROVED);
        requireStaff();
        return advance(application, Status.REJECTED, LoanApplicationStep.Type.REJECT, request.note(),
                "LOAN_APPLICATION_REJECTED");
    }

    /**
     * The customer no longer wants the loan.
     */
    @Transactional
    public LoanDtos.ApplicationDetail withdraw(UUID applicationId, LoanDtos.Step request) {
        LoanApplication application = lockInScope(applicationId);
        ConcurrentModificationException.assertVersion(request.version(), application.getVersion());
        requireStatus(application, Status.DRAFT, Status.SUBMITTED, Status.ASSESSED, Status.RECOMMENDED,
                Status.APPROVED);
        requireStaff();
        return advance(application, Status.WITHDRAWN, LoanApplicationStep.Type.WITHDRAW, request.note(),
                "LOAN_APPLICATION_WITHDRAWN");
    }

    @Transactional(readOnly = true)
    public LoanDtos.ApplicationDetail get(UUID applicationId) {
        return detail(loadInScope(applicationId));
    }

    @Transactional(readOnly = true)
    public PageResponse<LoanDtos.Application> search(UUID customerId, String status, PageRequest page) {
        BranchScope scope = CurrentActor.require().branchScope();
        Collection<UUID> branches = scope.allBranches() || scope.branchIds().isEmpty()
                ? Set.of(new UUID(0, 0)) : scope.branchIds();
        Page<LoanApplication> found = applications.search(TenantContext.requireTenantId(), scope.allBranches(),
                branches, customerId, status == null ? null : Status.valueOf(status), page);
        Map<UUID, LoanDtos.Application> summaries = summaries(found.getContent()).stream()
                .collect(Collectors.toMap(LoanDtos.Application::id, Function.identity()));
        return PageResponse.from(found, application -> summaries.get(application.getId()));
    }

    /**
     * The schedule the application's loan would have if disbursed today.
     */
    @Transactional(readOnly = true)
    public LoanDtos.SchedulePreview preview(UUID applicationId) {
        LoanApplication application = loadInScope(applicationId);
        LoanProductVersion version = productService.version(application.getProductVersionId());
        LocalDate today = businessDates.today();
        boolean approved = application.getApprovedAmount() != null;
        LocalDate firstDue = application.getFirstDueDate() != null && application.getFirstDueDate().isAfter(today)
                ? application.getFirstDueDate() : null;
        return loanTerms.preview(version, approved ? application.getApprovedAmount() : application.getRequestedAmount(),
                approved ? application.getApprovedInstallments() : application.getRequestedInstallments(), today,
                firstDue);
    }

    // ----------------------------------------------------------------------------------- guarantors, collateral

    @Transactional
    public LoanDtos.ApplicationDetail addGuarantor(UUID applicationId, LoanDtos.NewGuarantor request) {
        LoanApplication application = lockInScope(applicationId);
        requireStatus(application, UNDECIDED.toArray(Status[]::new));
        if (request.customerId() != null) {
            CustomerSummary guarantor = customerService.getSummary(request.customerId());
            if (guarantor.id().equals(application.getCustomerId())) {
                throw new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION,
                        "Borrowers cannot guarantee their own loan.");
            }
        }
        currencies.requireValidAmount(request.guaranteedAmount(), application.getCurrency());
        LoanGuarantor guarantor = guarantors.saveAndFlush(new LoanGuarantor(UuidV7.next(), application.getTenantId(),
                applicationId, request.customerId(), request.fullName().trim(), blankToNull(request.phone()),
                request.relationship().trim(), request.guaranteedAmount(), clock.instant()));
        auditService.record(AuditEvent.builder("LOAN_GUARANTOR_ADDED", RESOURCE)
                .resourceId(applicationId)
                .resourceReference(application.getApplicationNumber())
                .branchId(application.getBranchId())
                .metadata("guarantorId", guarantor.getId())
                .metadata("guaranteedAmount", currencies.present(guarantor.getGuaranteedAmount(),
                        application.getCurrency()))
                .build());
        return detail(application);
    }

    @Transactional
    public LoanDtos.ApplicationDetail verifyGuarantor(UUID applicationId, UUID guarantorId) {
        LoanApplication application = lockInScope(applicationId);
        requireStatus(application, UNDECIDED.toArray(Status[]::new));
        UUID verifier = requireVerifier(application);
        LoanGuarantor guarantor = guarantors.findByTenantIdAndId(application.getTenantId(), guarantorId)
                .filter(candidate -> candidate.getApplicationId().equals(applicationId))
                .orElseThrow(() -> new ResourceNotFoundException("Guarantor"));
        if (!guarantor.isVerified()) {
            guarantor.verify(verifier, clock.instant());
            guarantors.saveAndFlush(guarantor);
            auditService.record(AuditEvent.builder("LOAN_GUARANTOR_VERIFIED", RESOURCE)
                    .resourceId(applicationId)
                    .resourceReference(application.getApplicationNumber())
                    .branchId(application.getBranchId())
                    .metadata("guarantorId", guarantorId)
                    .build());
        }
        return detail(application);
    }

    @Transactional
    public LoanDtos.ApplicationDetail addCollateral(UUID applicationId, LoanDtos.NewCollateral request) {
        LoanApplication application = lockInScope(applicationId);
        requireStatus(application, UNDECIDED.toArray(Status[]::new));
        String currency = application.getCurrency();
        currencies.requireValidAmount(request.estimatedValue(), currency);
        currencies.requireValidAmount(request.forcedSaleValue(), currency);
        if (request.forcedSaleValue().compareTo(request.estimatedValue()) > 0
                || request.valuationDate().isAfter(businessDates.today())) {
            throw new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION,
                    "The forced-sale value cannot exceed the estimated value, and the valuation cannot be dated in"
                            + " the future.");
        }
        LoanCollateral collateral = collaterals.saveAndFlush(new LoanCollateral(UuidV7.next(),
                application.getTenantId(), applicationId, request.category(), request.description().trim(),
                request.estimatedValue(), request.forcedSaleValue(), request.valuationDate(), clock.instant()));
        auditService.record(AuditEvent.builder("LOAN_COLLATERAL_ADDED", RESOURCE)
                .resourceId(applicationId)
                .resourceReference(application.getApplicationNumber())
                .branchId(application.getBranchId())
                .metadata("collateralId", collateral.getId())
                .metadata("forcedSaleValue", currencies.present(collateral.getForcedSaleValue(), currency))
                .build());
        return detail(application);
    }

    @Transactional
    public LoanDtos.ApplicationDetail verifyCollateral(UUID applicationId, UUID collateralId) {
        LoanApplication application = lockInScope(applicationId);
        requireStatus(application, UNDECIDED.toArray(Status[]::new));
        UUID verifier = requireVerifier(application);
        LoanCollateral collateral = collateralOf(application, collateralId);
        if (!collateral.isVerified()) {
            collateral.verify(verifier, clock.instant());
            collaterals.saveAndFlush(collateral);
            auditService.record(AuditEvent.builder("LOAN_COLLATERAL_VERIFIED", RESOURCE)
                    .resourceId(applicationId)
                    .resourceReference(application.getApplicationNumber())
                    .branchId(application.getBranchId())
                    .metadata("collateralId", collateralId)
                    .build());
        }
        return detail(application);
    }

    /**
     * Hands collateral back once it secures nothing: the application was rejected or withdrawn, or its loan is
     * closed.
     */
    @Transactional
    public LoanDtos.ApplicationDetail releaseCollateral(UUID applicationId, UUID collateralId) {
        LoanApplication application = lockInScope(applicationId);
        boolean securesNothing = application.getStatus() == Status.REJECTED
                || application.getStatus() == Status.WITHDRAWN
                || loans.findByTenantIdAndApplicationId(application.getTenantId(), applicationId)
                .map(loan -> loan.getStatus() == Loan.Status.CLOSED).orElse(false);
        if (!securesNothing) {
            throw new BankingException(LoanErrorCode.INVALID_STEP,
                    "Collateral is released when the application is rejected or withdrawn, or the loan is repaid.");
        }
        LoanCollateral collateral = collateralOf(application, collateralId);
        if ("PLEDGED".equals(collateral.getStatus())) {
            collateral.release(clock.instant());
            collaterals.saveAndFlush(collateral);
            auditService.record(AuditEvent.builder("LOAN_COLLATERAL_RELEASED", RESOURCE)
                    .resourceId(applicationId)
                    .resourceReference(application.getApplicationNumber())
                    .branchId(application.getBranchId())
                    .metadata("collateralId", collateralId)
                    .build());
        }
        return detail(application);
    }

    // ------------------------------------------------------------------------------------- for the module

    /**
     * Locks an approved application for disbursement, after checking the caller may take that step.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    LoanApplication lockForDisbursement(UUID applicationId, UUID disburser) {
        LoanApplication application = lockInScope(applicationId);
        requireStatus(application, Status.APPROVED);
        requireSeparateActor(application, disburser);
        requireEligible(productService.version(application.getProductVersionId()),
                customerService.getSummary(application.getCustomerId()));
        return application;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void markDisbursed(LoanApplication application, UUID disburser, String note) {
        steps.saveAndFlush(new LoanApplicationStep(UuidV7.next(), application.getTenantId(), application.getId(),
                LoanApplicationStep.Type.DISBURSE, disburser, clock.instant(), blankToNull(note)));
        application.moveTo(Status.DISBURSED, clock.instant());
        applications.saveAndFlush(application);
    }

    /**
     * Releases the pledged collateral of a repaid loan's application.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void releaseAllCollateral(UUID applicationId) {
        List<LoanCollateral> pledged = collaterals.findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc(
                        TenantContext.requireTenantId(), applicationId).stream()
                .filter(collateral -> "PLEDGED".equals(collateral.getStatus()))
                .toList();
        Instant now = clock.instant();
        pledged.forEach(collateral -> collateral.release(now));
        collaterals.saveAllAndFlush(pledged);
    }

    // ------------------------------------------------------------------------------------------------ rules

    private LoanDtos.ApplicationDetail advance(LoanApplication application, Status status,
                                               LoanApplicationStep.Type step, String note, String event) {
        UUID actor = requireStaff();
        steps.saveAndFlush(new LoanApplicationStep(UuidV7.next(), application.getTenantId(), application.getId(), step,
                actor, clock.instant(), blankToNull(note)));
        application.moveTo(status, clock.instant());
        applications.saveAndFlush(application);
        LoanDtos.ApplicationDetail detail = detail(application);
        auditService.record(AuditEvent.builder(event, RESOURCE)
                .resourceId(application.getId())
                .resourceReference(application.getApplicationNumber())
                .branchId(application.getBranchId())
                .metadata("status", status.name())
                .metadata("note", blankToNull(note))
                .after(detail.application())
                .build());
        return detail;
    }

    /**
     * Only an active (KYC-approved) customer at or above the product's KYC tier may borrow.
     */
    private void requireEligible(LoanProductVersion version, CustomerSummary customer) {
        // ACTIVE is only reachable through KYC approval, so the customer has been verified.
        if (!"ACTIVE".equals(customer.status())) {
            throw new BankingException(LoanErrorCode.CUSTOMER_NOT_ELIGIBLE);
        }
        if (version.getRequiredKycTier() != null) {
            int required = kycTiers.requireActiveRank(version.getRequiredKycTier());
            if (customer.kycTierCode() == null || kycTiers.rankOf(customer.kycTierCode()) < required) {
                throw new BankingException(LoanErrorCode.KYC_TIER_TOO_LOW);
            }
        }
    }

    private void requireDisbursementAccount(UUID customerId, UUID accountId, String currency) {
        AccountSummary account = accountService.heldBy(customerId).stream()
                .filter(held -> held.id().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new BankingException(LoanErrorCode.INVALID_DISBURSEMENT_ACCOUNT));
        if (!"ACTIVE".equals(account.status()) || !currency.equals(account.currency())
                || !DISBURSABLE_ACCOUNTS.contains(account.productType())) {
            throw new BankingException(LoanErrorCode.INVALID_DISBURSEMENT_ACCOUNT);
        }
    }

    private void requireSecurity(LoanApplication application, LoanProductVersion version, BigDecimal amount) {
        LoanDtos.Security security = security(application, version, amount);
        if (security.guarantorsVerified() < security.guarantorsRequired()) {
            throw new BankingException(LoanErrorCode.GUARANTORS_REQUIRED);
        }
        if (security.collateralVerified().compareTo(security.collateralNeeded()) < 0) {
            throw new BankingException(LoanErrorCode.COLLATERAL_REQUIRED);
        }
    }

    private LoanDtos.Security security(LoanApplication application, LoanProductVersion version, BigDecimal amount) {
        UUID tenantId = application.getTenantId();
        int verifiedGuarantors = (int) guarantors.findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc(tenantId,
                application.getId()).stream().filter(LoanGuarantor::isVerified).count();
        BigDecimal verifiedCollateral = collaterals.findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc(tenantId,
                        application.getId()).stream()
                .filter(collateral -> collateral.isVerified() && "PLEDGED".equals(collateral.getStatus()))
                .map(LoanCollateral::getForcedSaleValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal needed = amount.multiply(version.getCollateralCoverage()).divide(HUNDRED)
                .setScale(loanTerms.minorUnits(version), RoundingMode.CEILING);
        String currency = application.getCurrency();
        return new LoanDtos.Security(version.getRequiredGuarantors(), verifiedGuarantors,
                currencies.present(needed, currency), currencies.present(verifiedCollateral, currency));
    }

    private static boolean secondApprovalRequired(LoanProductVersion version, BigDecimal amount) {
        return version.getSecondApprovalAbove() != null && amount.compareTo(version.getSecondApprovalAbove()) > 0;
    }

    private void requireSeparateActor(LoanApplication application, UUID actor) {
        boolean decidedBefore = steps.findAllByTenantIdAndApplicationIdOrderByOccurredAtAsc(application.getTenantId(),
                        application.getId()).stream()
                .anyMatch(step -> DECISIONS.contains(step.getStepType()) && step.getActorId().equals(actor));
        if (actor.equals(application.getLoanOfficerId()) || decidedBefore) {
            throw new BankingException(LoanErrorCode.SEPARATION_OF_DUTIES);
        }
    }

    private UUID requireVerifier(LoanApplication application) {
        UUID verifier = requireStaff();
        if (verifier.equals(application.getLoanOfficerId())) {
            throw new BankingException(LoanErrorCode.SEPARATION_OF_DUTIES,
                    "Guarantors and collateral are verified by someone other than the loan officer.");
        }
        return verifier;
    }

    private static void requireStatus(LoanApplication application, Status... allowed) {
        if (!EnumSet.copyOf(List.of(allowed)).contains(application.getStatus())) {
            throw new BankingException(LoanErrorCode.INVALID_STEP);
        }
    }

    private static UUID requireStaff() {
        return CurrentActor.currentActorId().orElseThrow(() -> new BankingException(CommonErrorCode.ACCESS_DENIED));
    }

    private BigDecimal optionalMoney(BigDecimal amount, String currency) {
        if (amount != null && amount.signum() > 0) {
            currencies.requireValidAmount(amount, currency);
        }
        return amount;
    }

    private LoanCollateral collateralOf(LoanApplication application, UUID collateralId) {
        return collaterals.findByTenantIdAndId(application.getTenantId(), collateralId)
                .filter(candidate -> candidate.getApplicationId().equals(application.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Collateral"));
    }

    private LoanApplication loadInScope(UUID applicationId) {
        BranchScope scope = CurrentActor.require().branchScope();
        return applications.findByTenantIdAndId(TenantContext.requireTenantId(), applicationId)
                .filter(application -> scope.permits(application.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Loan application"));
    }

    private LoanApplication lockInScope(UUID applicationId) {
        loadInScope(applicationId);
        return applications.lockByTenantIdAndId(TenantContext.requireTenantId(), applicationId).orElseThrow();
    }

    // -------------------------------------------------------------------------------------------- responses

    private LoanDtos.ApplicationDetail detail(LoanApplication application) {
        UUID tenantId = application.getTenantId();
        List<LoanApplicationStep> trail = steps.findAllByTenantIdAndApplicationIdOrderByOccurredAtAsc(tenantId,
                application.getId());
        Set<UUID> people = new HashSet<>();
        trail.forEach(step -> people.add(step.getActorId()));
        Map<UUID, String> names = staffService.names(people);
        String currency = application.getCurrency();
        LoanProductVersion version = productService.version(application.getProductVersionId());
        BigDecimal amount = application.getApprovedAmount() != null ? application.getApprovedAmount()
                : application.getRequestedAmount();
        return new LoanDtos.ApplicationDetail(summaries(List.of(application)).getFirst(),
                trail.stream().map(step -> new LoanDtos.WorkflowStep(step.getStepType().name(), step.getActorId(),
                        names.get(step.getActorId()), step.getOccurredAt(), step.getNote())).toList(),
                guarantors.findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc(tenantId, application.getId()).stream()
                        .map(guarantor -> new LoanDtos.Guarantor(guarantor.getId(), guarantor.getCustomerId(),
                                guarantor.getFullName(), guarantor.getPhone(), guarantor.getRelationship(),
                                currencies.present(guarantor.getGuaranteedAmount(), currency),
                                guarantor.getVerifiedBy(), guarantor.getVerifiedAt()))
                        .toList(),
                collaterals.findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc(tenantId, application.getId()).stream()
                        .map(collateral -> new LoanDtos.Collateral(collateral.getId(), collateral.getCategory(),
                                collateral.getDescription(), currencies.present(collateral.getEstimatedValue(),
                                currency), currencies.present(collateral.getForcedSaleValue(), currency),
                                collateral.getValuationDate(), collateral.getStatus(), collateral.getVerifiedBy(),
                                collateral.getVerifiedAt(), collateral.getReleasedAt()))
                        .toList(),
                security(application, version, amount));
    }

    private List<LoanDtos.Application> summaries(List<LoanApplication> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        UUID tenantId = page.getFirst().getTenantId();
        Map<UUID, CustomerSummary> customers = customerService.summaries(page.stream()
                .map(LoanApplication::getCustomerId).collect(Collectors.toSet()));
        Map<UUID, String> officers = staffService.names(page.stream().map(LoanApplication::getLoanOfficerId)
                .collect(Collectors.toSet()));
        Map<UUID, LoanProduct> products = productService.productsById(page.stream()
                .map(LoanApplication::getProductId).collect(Collectors.toSet()));
        Map<UUID, UUID> loanOf = loans.findAllByTenantIdAndApplicationIdIn(tenantId, page.stream()
                        .map(LoanApplication::getId).toList()).stream()
                .collect(Collectors.toMap(Loan::getApplicationId, Loan::getId));
        Map<UUID, LoanProductVersion> versions = page.stream().map(LoanApplication::getProductVersionId).distinct()
                .collect(Collectors.toMap(Function.identity(), productService::version));
        return page.stream().map(application -> {
            String currency = application.getCurrency();
            CustomerSummary customer = customers.get(application.getCustomerId());
            LoanProduct product = products.get(application.getProductId());
            BigDecimal amount = application.getApprovedAmount() != null ? application.getApprovedAmount()
                    : application.getRequestedAmount();
            return new LoanDtos.Application(application.getId(), application.getApplicationNumber(),
                    application.getCustomerId(), customer == null ? null : customer.displayName(),
                    application.getBranchId(), application.getProductId(), product == null ? null : product.getCode(),
                    product == null ? null : product.getName(), application.getProductVersionId(), currency,
                    currencies.present(application.getRequestedAmount(), currency),
                    application.getRequestedInstallments(), application.getPurpose(),
                    present(application.getMonthlyIncome(), currency),
                    present(application.getMonthlyExpenses(), currency),
                    present(application.getExistingDebt(), currency), application.getDisbursementAccountId(),
                    application.getStatus().name(), application.getRiskRating(), application.getAssessmentNote(),
                    present(application.getApprovedAmount(), currency), application.getApprovedInstallments(),
                    application.getFirstDueDate(), application.getLoanOfficerId(),
                    officers.get(application.getLoanOfficerId()),
                    secondApprovalRequired(versions.get(application.getProductVersionId()), amount),
                    loanOf.get(application.getId()), application.getCreatedAt(), application.getUpdatedAt(),
                    application.getVersion());
        }).toList();
    }

    private BigDecimal present(BigDecimal amount, String currency) {
        return amount == null ? null : currencies.present(amount, currency);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
