package com.company.banking.product.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.DuplicateResourceException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.kyc.service.KycTierService;
import com.company.banking.ledger.dto.GlAccountRef;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.ledger.service.ChartOfAccountService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.product.dto.ChargeRequest;
import com.company.banking.product.dto.ChargeTerms;
import com.company.banking.product.dto.CreateProductRequest;
import com.company.banking.product.dto.ProductRef;
import com.company.banking.product.dto.ProductResponse;
import com.company.banking.product.dto.ProductTerms;
import com.company.banking.product.dto.ProductTermsRequest;
import com.company.banking.product.dto.ProductVersionResponse;
import com.company.banking.product.dto.UpdateProductRequest;
import com.company.banking.product.entity.AccountProduct;
import com.company.banking.product.entity.AccountProductVersion;
import com.company.banking.product.entity.ProductCharge;
import com.company.banking.product.entity.ProductStatus;
import com.company.banking.product.entity.ProductVersionStatus;
import com.company.banking.product.exception.ProductErrorCode;
import com.company.banking.product.model.ChargeCalculation;
import com.company.banking.product.model.ChargeEvent;
import com.company.banking.product.model.ProductType;
import com.company.banking.product.repository.AccountProductRepository;
import com.company.banking.product.repository.AccountProductVersionRepository;
import com.company.banking.product.repository.ProductChargeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deposit products and their versioned terms (decision D9). Accounts are bound to the version they were opened
 * under; publishing a new version changes the terms for new accounts only.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String RESOURCE = "ACCOUNT_PRODUCT";
    private static final int DEFAULT_DORMANCY_DAYS = 365;

    private static final Map<ProductType, SystemAccount> DEPOSIT_GL = Map.of(
            ProductType.SAVINGS, SystemAccount.SAVINGS_DEPOSITS,
            ProductType.CURRENT, SystemAccount.CURRENT_DEPOSITS,
            ProductType.SUSU, SystemAccount.SUSU_DEPOSITS,
            ProductType.FIXED_DEPOSIT, SystemAccount.FIXED_DEPOSITS,
            ProductType.TARGET_SAVINGS, SystemAccount.TARGET_SAVINGS_DEPOSITS);

    private final AccountProductRepository products;
    private final AccountProductVersionRepository versions;
    private final ProductChargeRepository charges;
    private final ChartOfAccountService chartOfAccounts;
    private final CurrencyService currencies;
    private final KycTierService kycTiers;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        UUID tenantId = TenantContext.requireTenantId();
        return products.findAllByTenantIdOrderByCode(tenantId).stream().map(product -> toResponse(tenantId, product))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID productId) {
        UUID tenantId = TenantContext.requireTenantId();
        return toResponse(tenantId, loadProduct(tenantId, productId));
    }

    /**
     * Creates a product with a draft first version; publish it to start opening accounts.
     */
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String code = request.code().trim();
        if (products.existsByTenantIdAndCode(tenantId, code)) {
            throw new DuplicateResourceException("A product with this code already exists.");
        }
        ProductType type = ProductType.valueOf(request.productType());
        AccountProduct product = products.saveAndFlush(new AccountProduct(UuidV7.next(), tenantId, code,
                request.name().trim(), type, blankToNull(request.description())));
        AccountProductVersion version = versions.saveAndFlush(new AccountProductVersion(UuidV7.next(), tenantId,
                product.getId(), 1, resolveTerms(type, request.terms()), clock.instant(),
                CurrentActor.currentActorId().orElse(null)));
        replaceCharges(version, request.terms().charges());
        ProductResponse response = toResponse(tenantId, product);
        auditService.record(AuditEvent.builder("PRODUCT_CREATED", RESOURCE)
                .resourceId(product.getId())
                .resourceReference(code)
                .after(response)
                .build());
        return response;
    }

    @Transactional
    public ProductResponse update(UUID productId, UpdateProductRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        AccountProduct product = loadProduct(tenantId, productId);
        ConcurrentModificationException.assertVersion(request.version(), product.getVersion());
        ProductResponse before = toResponse(tenantId, product);
        product.rename(request.name().trim(), blankToNull(request.description()));
        product.changeStatus(ProductStatus.valueOf(request.status()));
        products.saveAndFlush(product);
        ProductResponse after = toResponse(tenantId, product);
        auditService.record(AuditEvent.builder("PRODUCT_UPDATED", RESOURCE)
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
    public ProductResponse createDraft(UUID productId) {
        UUID tenantId = TenantContext.requireTenantId();
        AccountProduct product = loadProduct(tenantId, productId);
        List<AccountProductVersion> all =
                versions.findAllByTenantIdAndProductIdOrderByVersionNoDesc(tenantId, productId);
        if (all.stream().anyMatch(version -> version.getStatus() == ProductVersionStatus.DRAFT)) {
            throw new BankingException(ProductErrorCode.DRAFT_ALREADY_EXISTS);
        }
        AccountProductVersion latest = all.getFirst();
        AccountProductVersion draft = versions.saveAndFlush(new AccountProductVersion(UuidV7.next(), tenantId,
                productId, latest.getVersionNo() + 1, latest.terms(), clock.instant(),
                CurrentActor.currentActorId().orElse(null)));
        charges.saveAllAndFlush(charges.findAllByTenantIdAndProductVersionIdOrderByChargeEvent(tenantId,
                        latest.getId()).stream()
                .map(charge -> new ProductCharge(UuidV7.next(), tenantId, draft.getId(), charge.getChargeEvent(),
                        charge.getName(), charge.getCalculation(), charge.getFlatAmount(), charge.getRate(),
                        charge.getMinAmount(), charge.getMaxAmount()))
                .toList());
        return toResponse(tenantId, product);
    }

    @Transactional
    public ProductResponse updateDraft(UUID productId, UUID versionId, ProductTermsRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        AccountProduct product = loadProduct(tenantId, productId);
        AccountProductVersion draft = loadVersion(tenantId, productId, versionId);
        if (draft.getStatus() != ProductVersionStatus.DRAFT) {
            throw new BankingException(ProductErrorCode.VERSION_NOT_EDITABLE);
        }
        draft.apply(resolveTerms(product.getProductType(), request));
        versions.saveAndFlush(draft);
        replaceCharges(draft, request.charges());
        auditService.record(AuditEvent.builder("PRODUCT_DRAFT_UPDATED", RESOURCE)
                .resourceId(productId)
                .resourceReference(product.getCode())
                .metadata("versionNo", draft.getVersionNo())
                .after(toVersionResponse(draft))
                .build());
        return toResponse(tenantId, product);
    }

    /**
     * Publishes a draft: it becomes the terms for new accounts and the previous published version is retired
     * (existing accounts keep their terms).
     */
    @Transactional
    public ProductResponse publish(UUID productId, UUID versionId) {
        UUID tenantId = TenantContext.requireTenantId();
        AccountProduct product = loadProduct(tenantId, productId);
        AccountProductVersion draft = loadVersion(tenantId, productId, versionId);
        if (draft.getStatus() != ProductVersionStatus.DRAFT) {
            throw new BankingException(ProductErrorCode.VERSION_NOT_EDITABLE);
        }
        // Re-validate: GL accounts or KYC tiers referenced by the draft may have changed since it was edited.
        resolveTerms(product.getProductType(), toRequest(draft.terms()));
        versions.findAllByTenantIdAndProductIdAndStatus(tenantId, productId, ProductVersionStatus.PUBLISHED)
                .forEach(previous -> {
                    previous.retire();
                    versions.save(previous);
                });
        draft.publish(clock.instant(), CurrentActor.currentActorId().orElse(null));
        versions.save(draft);
        product.publish(draft.getId());
        products.saveAndFlush(product);
        versions.flush();
        ProductResponse response = toResponse(tenantId, product);
        auditService.record(AuditEvent.builder("PRODUCT_VERSION_PUBLISHED", RESOURCE)
                .resourceId(productId)
                .resourceReference(product.getCode())
                .metadata("versionNo", draft.getVersionNo())
                .after(response.currentVersion())
                .build());
        return response;
    }

    /**
     * Terms for opening a new account: the product must be active and have published terms.
     */
    @Transactional(readOnly = true)
    public ProductTerms termsForNewAccount(UUID productId) {
        UUID tenantId = TenantContext.requireTenantId();
        AccountProduct product = products.findByTenantIdAndId(tenantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product"));
        if (product.getStatus() != ProductStatus.ACTIVE || product.getCurrentVersionId() == null) {
            throw new BankingException(ProductErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        return toTerms(product, loadVersion(tenantId, productId, product.getCurrentVersionId()));
    }

    /**
     * The terms of a specific version (an account's contract).
     */
    @Transactional(readOnly = true)
    public ProductTerms terms(UUID versionId) {
        UUID tenantId = TenantContext.requireTenantId();
        AccountProductVersion version = versions.findByTenantIdAndId(tenantId, versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Product version"));
        return toTerms(loadProduct(tenantId, version.getProductId()), version);
    }

    /**
     * Versions whose accounts earn interest (a rate above zero and a posting frequency), for end-of-day.
     */
    @Transactional(readOnly = true)
    public List<UUID> interestBearingVersionIds() {
        return versions.interestBearingIds(TenantContext.requireTenantId(), ProductVersionStatus.DRAFT);
    }

    @Transactional(readOnly = true)
    public Map<UUID, ProductRef> refs(Collection<UUID> productIds) {
        UUID tenantId = TenantContext.requireTenantId();
        return products.findAllById(productIds).stream()
                .filter(product -> product.getTenantId().equals(tenantId))
                .collect(Collectors.toMap(AccountProduct::getId, product -> new ProductRef(product.getId(),
                        product.getCode(), product.getName(), product.getProductType().name())));
    }

    private AccountProductVersion.Terms resolveTerms(ProductType type, ProductTermsRequest request) {
        String currency = currencies.require(request.currency()).code();
        GlAccountRef depositGl = gl(request.depositGlId(), DEPOSIT_GL.get(type), "LIABILITY");
        GlAccountRef feeGl = gl(request.feeIncomeGlId(), SystemAccount.ACCOUNT_FEE_INCOME, "INCOME");
        GlAccountRef interestGl = gl(request.interestExpenseGlId(), SystemAccount.DEPOSIT_INTEREST_EXPENSE, "EXPENSE");

        BigDecimal minOpening = money(request.minOpeningBalance(), currency);
        BigDecimal minOperating = money(request.minOperatingBalance(), currency);
        BigDecimal maxBalance = optionalMoney(request.maxBalance(), currency);
        boolean overdraftLimitSet = request.maxOverdraftLimit() != null && request.maxOverdraftLimit().signum() > 0;
        if (!request.allowOverdraft() && overdraftLimitSet) {
            throw new BankingException(ProductErrorCode.INVALID_TERMS,
                    "An overdraft limit needs overdrafts to be allowed.");
        }
        BigDecimal maxOverdraft = money(request.maxOverdraftLimit(), currency);
        BigDecimal maxWithdrawal = optionalMoney(request.maxWithdrawalAmount(), currency);
        BigDecimal dailyLimit = optionalMoney(request.dailyWithdrawalLimit(), currency);
        BigDecimal rate = request.interestRate() == null ? BigDecimal.ZERO : request.interestRate();
        if (rate.stripTrailingZeros().scale() > 6) {
            throw new BankingException(ProductErrorCode.INVALID_TERMS, "Interest rates have at most 6 decimal places.");
        }
        if ((maxBalance != null && maxBalance.compareTo(minOpening) < 0)
                || (maxWithdrawal != null && dailyLimit != null && maxWithdrawal.compareTo(dailyLimit) > 0)) {
            throw new BankingException(ProductErrorCode.INVALID_TERMS,
                    "The maximum balance must cover the opening balance, and one withdrawal cannot exceed the "
                            + "daily limit.");
        }
        String requiredTier = blankToNull(request.requiredKycTier());
        if (requiredTier != null) {
            kycTiers.requireActiveRank(requiredTier);
        }
        return new AccountProductVersion.Terms(currency, depositGl.id(), feeGl.id(), interestGl.id(), minOpening,
                minOperating, maxBalance, rate,
                request.interestCalcMethod() == null ? "DAILY_BALANCE" : request.interestCalcMethod(),
                request.interestPostingFrequency() == null ? (rate.signum() == 0 ? "NONE" : "MONTHLY")
                        : request.interestPostingFrequency(),
                request.dayCount() == null ? "ACTUAL_365F" : request.dayCount(),
                request.dormancyDays() == null ? DEFAULT_DORMANCY_DAYS : request.dormancyDays(),
                requiredTier, request.allowOverdraft(), maxOverdraft, maxWithdrawal, dailyLimit);
    }

    /**
     * Replaces the charges of a draft version (the database refuses it for any other version).
     */
    private void replaceCharges(AccountProductVersion version, List<ChargeRequest> requested) {
        String currency = version.getCurrency();
        Set<String> events = new HashSet<>();
        List<ProductCharge> replacement = new ArrayList<>();
        for (ChargeRequest charge : requested == null ? List.<ChargeRequest>of() : requested) {
            if (!events.add(charge.event())) {
                throw new BankingException(ProductErrorCode.INVALID_TERMS,
                        "A product can have one charge per kind of money movement.");
            }
            ChargeCalculation calculation = ChargeCalculation.valueOf(charge.calculation());
            boolean consistent = calculation == ChargeCalculation.FLAT
                    ? charge.flatAmount() != null && charge.rate() == null && charge.minAmount() == null
                    && charge.maxAmount() == null
                    : charge.rate() != null && charge.flatAmount() == null;
            if (!consistent) {
                throw new BankingException(ProductErrorCode.INVALID_TERMS,
                        "A flat charge has only an amount; a percentage charge has a rate and optionally a minimum "
                                + "and maximum.");
            }
            if (calculation == ChargeCalculation.FLAT) {
                currencies.requireValidAmount(charge.flatAmount(), currency);
            } else if (charge.rate().stripTrailingZeros().scale() > 6) {
                throw new BankingException(ProductErrorCode.INVALID_TERMS, "Charge rates have at most 6 decimal places.");
            }
            BigDecimal min = charge.minAmount() == null ? null : money(charge.minAmount(), currency);
            BigDecimal max = optionalMoney(charge.maxAmount(), currency);
            if (min != null && max != null && min.compareTo(max) > 0) {
                throw new BankingException(ProductErrorCode.INVALID_TERMS,
                        "A charge minimum cannot be above its maximum.");
            }
            replacement.add(new ProductCharge(UuidV7.next(), version.getTenantId(), version.getId(),
                    ChargeEvent.valueOf(charge.event()), charge.name().trim(), calculation, charge.flatAmount(),
                    charge.rate(), min, max));
        }
        charges.deleteAll(charges.findAllByTenantIdAndProductVersionIdOrderByChargeEvent(version.getTenantId(),
                version.getId()));
        charges.flush();
        charges.saveAllAndFlush(replacement);
    }

    private List<ChargeTerms> chargesOf(AccountProductVersion version) {
        String currency = version.getCurrency();
        return charges.findAllByTenantIdAndProductVersionIdOrderByChargeEvent(version.getTenantId(), version.getId())
                .stream()
                .map(charge -> new ChargeTerms(charge.getChargeEvent().name(), charge.getName(),
                        charge.getCalculation().name(), present(charge.getFlatAmount(), currency),
                        charge.getRate() == null ? null : charge.getRate().stripTrailingZeros(),
                        present(charge.getMinAmount(), currency), present(charge.getMaxAmount(), currency)))
                .toList();
    }

    private GlAccountRef gl(UUID requested, SystemAccount fallback, String expectedClass) {
        GlAccountRef gl = requested != null ? chartOfAccounts.requirePostable(requested)
                : chartOfAccounts.requireSystem(fallback);
        if (!expectedClass.equals(gl.accountClass())) {
            throw new BankingException(ProductErrorCode.INVALID_GL_MAPPING);
        }
        return gl;
    }

    private BigDecimal money(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() == 0) {
            return BigDecimal.ZERO;
        }
        currencies.requireValidAmount(amount, currency);
        return amount;
    }

    private BigDecimal optionalMoney(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        currencies.requireValidAmount(amount, currency);
        return amount;
    }

    private static ProductTermsRequest toRequest(AccountProductVersion.Terms terms) {
        return new ProductTermsRequest(terms.currency(), terms.depositGlId(), terms.feeIncomeGlId(),
                terms.interestExpenseGlId(), terms.minOpeningBalance(), terms.minOperatingBalance(), terms.maxBalance(),
                terms.interestRate(), terms.interestCalcMethod(), terms.interestPostingFrequency(), terms.dayCount(),
                terms.dormancyDays(), terms.requiredKycTier(), terms.allowOverdraft(), terms.maxOverdraftLimit(),
                terms.maxWithdrawalAmount(), terms.dailyWithdrawalLimit(), null);
    }

    private AccountProduct loadProduct(UUID tenantId, UUID productId) {
        return products.findByTenantIdAndId(tenantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product"));
    }

    private AccountProductVersion loadVersion(UUID tenantId, UUID productId, UUID versionId) {
        return versions.findByTenantIdAndId(tenantId, versionId)
                .filter(version -> version.getProductId().equals(productId))
                .orElseThrow(() -> new ResourceNotFoundException("Product version"));
    }

    private ProductResponse toResponse(UUID tenantId, AccountProduct product) {
        List<ProductVersionResponse> all = versions
                .findAllByTenantIdAndProductIdOrderByVersionNoDesc(tenantId, product.getId()).stream()
                .map(this::toVersionResponse)
                .toList();
        ProductVersionResponse current = all.stream()
                .filter(version -> version.id().equals(product.getCurrentVersionId()))
                .findFirst()
                .orElse(null);
        return new ProductResponse(product.getId(), product.getCode(), product.getName(),
                product.getProductType().name(), product.getDescription(), product.getStatus().name(), current, all,
                product.getVersion());
    }

    private ProductVersionResponse toVersionResponse(AccountProductVersion version) {
        String currency = version.getCurrency();
        return new ProductVersionResponse(version.getId(), version.getVersionNo(), version.getStatus().name(), currency,
                version.getDepositGlId(), version.getFeeIncomeGlId(), version.getInterestExpenseGlId(),
                present(version.getMinOpeningBalance(), currency), present(version.getMinOperatingBalance(), currency),
                present(version.getMaxBalance(), currency), version.getInterestRate().stripTrailingZeros(),
                version.getInterestCalcMethod(), version.getInterestPostingFrequency(), version.getDayCount(),
                version.getDormancyDays(), version.getRequiredKycTier(), version.isAllowOverdraft(),
                present(version.getMaxOverdraftLimit(), currency), present(version.getMaxWithdrawalAmount(), currency),
                present(version.getDailyWithdrawalLimit(), currency), version.getCreatedAt(), version.getPublishedAt(),
                chargesOf(version));
    }

    private ProductTerms toTerms(AccountProduct product, AccountProductVersion version) {
        return new ProductTerms(product.getId(), product.getCode(), product.getName(), product.getProductType().name(),
                version.getId(), version.getVersionNo(), version.getCurrency(), version.getDepositGlId(),
                version.getFeeIncomeGlId(), version.getInterestExpenseGlId(), version.getMinOpeningBalance(),
                version.getMinOperatingBalance(), version.getMaxBalance(), version.getRequiredKycTier(),
                version.isAllowOverdraft(), version.getMaxOverdraftLimit(), version.getMaxWithdrawalAmount(),
                version.getDailyWithdrawalLimit(), version.getDormancyDays(), version.getInterestRate(),
                version.getInterestCalcMethod(), version.getInterestPostingFrequency(), version.getDayCount(),
                chargesOf(version));
    }

    private BigDecimal present(BigDecimal amount, String currency) {
        return amount == null ? null : currencies.present(amount, currency);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
