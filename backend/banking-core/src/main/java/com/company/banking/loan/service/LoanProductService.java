package com.company.banking.loan.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.kyc.service.KycTierService;
import com.company.banking.ledger.dto.GlAccountRef;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.ledger.service.ChartOfAccountService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.loan.dto.LoanDtos;
import com.company.banking.loan.entity.LoanProduct;
import com.company.banking.loan.entity.LoanProductVersion;
import com.company.banking.loan.exception.LoanErrorCode;
import com.company.banking.loan.repository.LoanProductRepository;
import com.company.banking.loan.repository.LoanProductVersionRepository;
import com.company.banking.loan.schedule.InterestMethod;
import com.company.banking.loan.schedule.LoanDayCount;
import com.company.banking.loan.schedule.RepaymentAllocator;
import com.company.banking.loan.schedule.RepaymentFrequency;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loan products and their versioned terms. A product starts with a draft version; publishing a draft makes it the
 * terms of new applications and retires the previous version (applications and loans keep the version they were
 * made on). Published terms never change.
 */
@Service
@RequiredArgsConstructor
public class LoanProductService {

    static final String RESOURCE = "LOAN_PRODUCT";
    static final String DEFAULT_ALLOCATION = "PENALTY,FEE,INTEREST,PRINCIPAL";

    private final LoanProductRepository products;
    private final LoanProductVersionRepository versions;
    private final ChartOfAccountService chartOfAccounts;
    private final CurrencyService currencies;
    private final KycTierService kycTiers;
    private final BusinessDateService businessDates;
    private final LoanTerms loanTerms;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<LoanDtos.Product> list() {
        UUID tenantId = TenantContext.requireTenantId();
        return products.findAllByTenantIdOrderByCode(tenantId).stream().map(product -> toResponse(tenantId, product))
                .toList();
    }

    @Transactional(readOnly = true)
    public LoanDtos.Product get(UUID productId) {
        UUID tenantId = TenantContext.requireTenantId();
        return toResponse(tenantId, loadProduct(tenantId, productId));
    }

    /**
     * Creates a product with a draft first version; publish it to start taking applications.
     */
    @Transactional
    public LoanDtos.Product create(LoanDtos.NewProduct request) {
        UUID tenantId = TenantContext.requireTenantId();
        String code = request.code().trim();
        if (products.existsByTenantIdAndCode(tenantId, code)) {
            throw new BankingException(LoanErrorCode.PRODUCT_CODE_EXISTS);
        }
        LoanProduct product = products.saveAndFlush(new LoanProduct(UuidV7.next(), tenantId, code,
                request.name().trim(), blankToNull(request.description()), clock.instant(),
                CurrentActor.currentActorId().orElse(null)));
        versions.saveAndFlush(new LoanProductVersion(UuidV7.next(), tenantId, product.getId(), 1,
                resolveTerms(request.terms()), clock.instant()));
        LoanDtos.Product response = toResponse(tenantId, product);
        auditService.record(AuditEvent.builder("LOAN_PRODUCT_CREATED", RESOURCE)
                .resourceId(product.getId())
                .resourceReference(code)
                .after(response)
                .build());
        return response;
    }

    @Transactional
    public LoanDtos.Product update(UUID productId, LoanDtos.UpdateProduct request) {
        UUID tenantId = TenantContext.requireTenantId();
        LoanProduct product = loadProduct(tenantId, productId);
        ConcurrentModificationException.assertVersion(request.version(), product.getVersion());
        LoanDtos.Product before = toResponse(tenantId, product);
        product.rename(request.name().trim(), blankToNull(request.description()), clock.instant());
        product.changeStatus(request.status(), clock.instant());
        products.saveAndFlush(product);
        LoanDtos.Product after = toResponse(tenantId, product);
        auditService.record(AuditEvent.builder("LOAN_PRODUCT_UPDATED", RESOURCE)
                .resourceId(productId)
                .resourceReference(product.getCode())
                .before(before)
                .after(after)
                .build());
        return after;
    }

    /**
     * Starts a new draft from the latest version's terms.
     */
    @Transactional
    public LoanDtos.Product createDraft(UUID productId) {
        UUID tenantId = TenantContext.requireTenantId();
        LoanProduct product = loadProduct(tenantId, productId);
        List<LoanProductVersion> all = versions.findAllByTenantIdAndProductIdOrderByVersionNoDesc(tenantId, productId);
        if (all.stream().anyMatch(version -> version.getStatus() == LoanProductVersion.Status.DRAFT)) {
            throw new BankingException(LoanErrorCode.DRAFT_ALREADY_EXISTS);
        }
        LoanProductVersion latest = all.getFirst();
        versions.saveAndFlush(new LoanProductVersion(UuidV7.next(), tenantId, productId, latest.getVersionNo() + 1,
                latest.terms(), clock.instant()));
        return toResponse(tenantId, product);
    }

    @Transactional
    public LoanDtos.Product updateDraft(UUID productId, UUID versionId, LoanDtos.Terms request) {
        UUID tenantId = TenantContext.requireTenantId();
        LoanProduct product = loadProduct(tenantId, productId);
        LoanProductVersion draft = loadVersion(tenantId, productId, versionId);
        if (draft.getStatus() != LoanProductVersion.Status.DRAFT) {
            throw new BankingException(LoanErrorCode.VERSION_NOT_EDITABLE);
        }
        draft.apply(resolveTerms(request));
        versions.saveAndFlush(draft);
        auditService.record(AuditEvent.builder("LOAN_PRODUCT_DRAFT_UPDATED", RESOURCE)
                .resourceId(productId)
                .resourceReference(product.getCode())
                .metadata("versionNo", draft.getVersionNo())
                .after(toVersionResponse(draft))
                .build());
        return toResponse(tenantId, product);
    }

    /**
     * Publishes a draft: new applications use it, and the previously published version is retired.
     */
    @Transactional
    public LoanDtos.Product publish(UUID productId, UUID versionId) {
        UUID tenantId = TenantContext.requireTenantId();
        LoanProduct product = loadProduct(tenantId, productId);
        LoanProductVersion draft = loadVersion(tenantId, productId, versionId);
        if (draft.getStatus() != LoanProductVersion.Status.DRAFT) {
            throw new BankingException(LoanErrorCode.VERSION_NOT_EDITABLE);
        }
        // Re-validate: GL accounts or KYC tiers the draft names may have changed since it was edited.
        resolveTerms(toRequest(draft.terms()));
        versions.findByTenantIdAndProductIdAndStatus(tenantId, productId, LoanProductVersion.Status.PUBLISHED)
                .ifPresent(previous -> {
                    previous.retire();
                    versions.saveAndFlush(previous);
                });
        draft.publish(clock.instant());
        versions.saveAndFlush(draft);
        LoanDtos.Product response = toResponse(tenantId, product);
        auditService.record(AuditEvent.builder("LOAN_PRODUCT_VERSION_PUBLISHED", RESOURCE)
                .resourceId(productId)
                .resourceReference(product.getCode())
                .metadata("versionNo", draft.getVersionNo())
                .after(response.currentVersion())
                .build());
        return response;
    }

    /**
     * The schedule a loan of this product would have if disbursed today (a calculator for staff and customers).
     */
    @Transactional(readOnly = true)
    public LoanDtos.SchedulePreview preview(UUID productId, BigDecimal amount, int installments,
                                            LocalDate firstDueDate) {
        LoanProductVersion version = publishedVersion(productId);
        loanTerms.requireWithinLimits(version, amount, installments);
        return loanTerms.preview(version, amount, installments, businessDates.today(), firstDueDate);
    }

    // ------------------------------------------------------------------------------------- for the module

    /**
     * The terms new applications are made on: the product must be active and have a published version.
     */
    LoanProductVersion publishedVersion(UUID productId) {
        UUID tenantId = TenantContext.requireTenantId();
        LoanProduct product = products.findByTenantIdAndId(tenantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan product"));
        if (!product.isActive()) {
            throw new BankingException(LoanErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        return versions.findByTenantIdAndProductIdAndStatus(tenantId, productId, LoanProductVersion.Status.PUBLISHED)
                .orElseThrow(() -> new BankingException(LoanErrorCode.PRODUCT_NOT_AVAILABLE));
    }

    LoanProductVersion version(UUID versionId) {
        return versions.findByTenantIdAndId(TenantContext.requireTenantId(), versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan product version"));
    }

    Map<UUID, LoanProduct> productsById(Collection<UUID> productIds) {
        UUID tenantId = TenantContext.requireTenantId();
        return products.findAllById(productIds).stream()
                .filter(product -> product.getTenantId().equals(tenantId))
                .collect(Collectors.toMap(LoanProduct::getId, Function.identity()));
    }

    // ---------------------------------------------------------------------------------------------- terms

    private LoanProductVersion.Terms resolveTerms(LoanDtos.Terms request) {
        String currency = currencies.require(request.currency()).code();
        currencies.requireValidAmount(request.minAmount(), currency);
        currencies.requireValidAmount(request.maxAmount(), currency);
        int principalGrace = request.principalGrace() == null ? 0 : request.principalGrace();
        int interestGrace = request.interestGrace() == null ? 0 : request.interestGrace();
        if (request.maxAmount().compareTo(request.minAmount()) < 0
                || request.maxInstallments() < request.minInstallments()) {
            throw new BankingException(LoanErrorCode.INVALID_TERMS, "The maximum must not be below the minimum.");
        }
        if (interestGrace > principalGrace || principalGrace >= request.minInstallments()) {
            throw new BankingException(LoanErrorCode.INVALID_TERMS, "Grace periods must leave at least one"
                    + " installment that repays principal, and interest grace may not exceed principal grace.");
        }
        String allocation = request.allocationOrder() == null ? DEFAULT_ALLOCATION : request.allocationOrder();
        try {
            RepaymentAllocator.parseOrder(allocation);
        } catch (IllegalArgumentException invalid) {
            throw new BankingException(LoanErrorCode.INVALID_TERMS,
                    "The allocation order must name penalty, fee, interest and principal once each.");
        }
        BigDecimal flatFee = request.processingFeeFlat() == null ? BigDecimal.ZERO : request.processingFeeFlat();
        if (flatFee.signum() > 0) {
            currencies.requireValidAmount(flatFee, currency);
        }
        if (request.secondApprovalAbove() != null) {
            currencies.requireValidAmount(request.secondApprovalAbove(), currency);
        }
        String requiredTier = blankToNull(request.requiredKycTier());
        if (requiredTier != null) {
            kycTiers.requireActiveRank(requiredTier);
        }
        return new LoanProductVersion.Terms(currency, request.minAmount(), request.maxAmount(),
                request.minInstallments(), request.maxInstallments(), InterestMethod.valueOf(request.interestMethod()),
                request.annualRate(), LoanDayCount.valueOf(request.dayCount()),
                RepaymentFrequency.valueOf(request.repaymentFrequency()), principalGrace, interestGrace,
                request.roundingMode() == null ? "HALF_EVEN" : request.roundingMode(), allocation,
                orZero(request.processingFeeRate()), flatFee, orZero(request.penaltyRate()),
                request.penaltyGraceDays() == null ? 0 : request.penaltyGraceDays(),
                request.requiredGuarantors() == null ? 0 : request.requiredGuarantors(),
                orZero(request.collateralCoverage()), request.secondApprovalAbove(), requiredTier,
                gl(request.principalGlId(), SystemAccount.LOAN_PRINCIPAL, "ASSET", "DEBIT").id(),
                gl(request.interestReceivableGlId(), SystemAccount.INTEREST_RECEIVABLE, "ASSET", "DEBIT").id(),
                gl(request.interestIncomeGlId(), SystemAccount.LOAN_INTEREST_INCOME, "INCOME", "CREDIT").id(),
                gl(request.feeIncomeGlId(), SystemAccount.LOAN_FEE_INCOME, "INCOME", "CREDIT").id(),
                gl(request.penaltyReceivableGlId(), SystemAccount.PENALTY_RECEIVABLE, "ASSET", "DEBIT").id(),
                gl(request.penaltyIncomeGlId(), SystemAccount.LOAN_PENALTY_INCOME, "INCOME", "CREDIT").id());
    }

    /**
     * Receivables must be debit-normal assets (not, say, the loan loss allowance) and income credit-normal.
     */
    private GlAccountRef gl(UUID requested, SystemAccount fallback, String expectedClass, String expectedSide) {
        GlAccountRef gl = requested != null ? chartOfAccounts.requirePostable(requested)
                : chartOfAccounts.requireSystem(fallback);
        if (!expectedClass.equals(gl.accountClass()) || !expectedSide.equals(gl.normalSide())) {
            throw new BankingException(LoanErrorCode.INVALID_GL_MAPPING);
        }
        return gl;
    }

    private static LoanDtos.Terms toRequest(LoanProductVersion.Terms terms) {
        return new LoanDtos.Terms(terms.currency(), terms.minAmount(), terms.maxAmount(), terms.minInstallments(),
                terms.maxInstallments(), terms.interestMethod().name(), terms.annualRate(), terms.dayCount().name(),
                terms.repaymentFrequency().name(), terms.principalGrace(), terms.interestGrace(),
                terms.roundingMode(), terms.allocationOrder(), terms.processingFeeRate(), terms.processingFeeFlat(),
                terms.penaltyRate(), terms.penaltyGraceDays(), terms.requiredGuarantors(),
                terms.collateralCoverage(), terms.secondApprovalAbove(), terms.requiredKycTier(),
                terms.principalGlId(), terms.interestReceivableGlId(), terms.interestIncomeGlId(),
                terms.feeIncomeGlId(), terms.penaltyReceivableGlId(), terms.penaltyIncomeGlId());
    }

    // ------------------------------------------------------------------------------------------- responses

    private LoanProduct loadProduct(UUID tenantId, UUID productId) {
        return products.findByTenantIdAndId(tenantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan product"));
    }

    private LoanProductVersion loadVersion(UUID tenantId, UUID productId, UUID versionId) {
        return versions.findByTenantIdAndId(tenantId, versionId)
                .filter(version -> version.getProductId().equals(productId))
                .orElseThrow(() -> new ResourceNotFoundException("Loan product version"));
    }

    private LoanDtos.Product toResponse(UUID tenantId, LoanProduct product) {
        List<LoanDtos.ProductVersion> all = versions
                .findAllByTenantIdAndProductIdOrderByVersionNoDesc(tenantId, product.getId()).stream()
                .map(this::toVersionResponse)
                .toList();
        LoanDtos.ProductVersion current = all.stream()
                .filter(version -> LoanProductVersion.Status.PUBLISHED.name().equals(version.status()))
                .findFirst()
                .orElse(null);
        return new LoanDtos.Product(product.getId(), product.getCode(), product.getName(), product.getDescription(),
                product.getStatus(), current, all, product.getVersion());
    }

    private LoanDtos.ProductVersion toVersionResponse(LoanProductVersion version) {
        String currency = version.getCurrency();
        return new LoanDtos.ProductVersion(version.getId(), version.getVersionNo(), version.getStatus().name(),
                currency, currencies.present(version.getMinAmount(), currency),
                currencies.present(version.getMaxAmount(), currency), version.getMinInstallments(),
                version.getMaxInstallments(), version.getInterestMethod().name(),
                version.getAnnualRate().stripTrailingZeros(), version.getDayCount().name(),
                version.getRepaymentFrequency().name(), version.getPrincipalGrace(), version.getInterestGrace(),
                version.getRoundingMode(), version.getAllocationOrder(),
                version.getProcessingFeeRate().stripTrailingZeros(),
                currencies.present(version.getProcessingFeeFlat(), currency),
                version.getPenaltyRate().stripTrailingZeros(), version.getPenaltyGraceDays(),
                version.getRequiredGuarantors(), version.getCollateralCoverage().stripTrailingZeros(),
                version.getSecondApprovalAbove() == null ? null
                        : currencies.present(version.getSecondApprovalAbove(), currency),
                version.getRequiredKycTier(), version.getPrincipalGlId(), version.getInterestReceivableGlId(),
                version.getInterestIncomeGlId(), version.getFeeIncomeGlId(), version.getPenaltyReceivableGlId(),
                version.getPenaltyIncomeGlId(), version.getCreatedAt(), version.getPublishedAt());
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
