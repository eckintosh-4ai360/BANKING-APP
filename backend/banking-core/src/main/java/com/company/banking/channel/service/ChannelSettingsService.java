package com.company.banking.channel.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.branch.service.BranchService;
import com.company.banking.channel.dto.ChannelSettingsDtos;
import com.company.banking.channel.entity.ChannelSettings;
import com.company.banking.channel.repository.ChannelSettingsRepository;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.kyc.service.KycTierService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.product.dto.ProductTerms;
import com.company.banking.product.service.ProductService;
import com.company.banking.tenant.service.TenantService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The institution's settings for the customer app. Transfers are limited in its base currency; a new institution
 * starts with conservative defaults to adjust: 5,000 a transfer, 10,000 a day, and 1,000 a transfer for 24 hours
 * after a beneficiary is added or a new device is trusted. Sign-up in the app starts off.
 */
@Service
@RequiredArgsConstructor
public class ChannelSettingsService {

    private static final String RESOURCE = "CUSTOMER_CHANNEL_SETTINGS";
    private static final Set<String> SIGN_UP_PRODUCT_TYPES = Set.of("SAVINGS", "CURRENT");

    private final ChannelSettingsRepository settings;
    private final TenantService tenantService;
    private final CurrencyService currencies;
    private final AuditService auditService;
    private final BranchService branchService;
    private final KycTierService tierService;
    private final ProductService productService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ChannelSettingsDtos.Settings get() {
        return toResponse(load());
    }

    @Transactional
    public ChannelSettingsDtos.Settings update(ChannelSettingsDtos.Update request) {
        ChannelSettings current = load();
        ConcurrentModificationException.assertVersion(request.version(), current.getVersion());
        String currency = tenantService.getCurrent().baseCurrency();
        for (BigDecimal amount : new BigDecimal[]{request.maxTransferAmount(), request.dailyTransferLimit(),
                request.cooldownMaxAmount(), request.newDeviceMaxAmount()}) {
            currencies.requireValidAmount(amount, currency);
        }
        if (request.dailyTransferLimit().compareTo(request.maxTransferAmount()) < 0
                || request.cooldownMaxAmount().compareTo(request.maxTransferAmount()) > 0
                || request.newDeviceMaxAmount().compareTo(request.maxTransferAmount()) > 0) {
            throw new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION, "The daily limit must cover one"
                    + " transfer, and the cooldown limits may not exceed the limit of one transfer.");
        }
        ChannelSettingsDtos.Settings before = toResponse(current);
        current.update(request.maxTransferAmount(), request.dailyTransferLimit(), request.beneficiaryCooldownHours(),
                request.cooldownMaxAmount(), request.newDeviceCooldownHours(), request.newDeviceMaxAmount(),
                clock.instant(), CurrentActor.currentActorId().orElse(null));
        settings.saveAndFlush(current);
        ChannelSettingsDtos.Settings after = toResponse(current);
        auditService.record(AuditEvent.builder("CUSTOMER_CHANNEL_SETTINGS_UPDATED", RESOURCE)
                .before(before)
                .after(after)
                .build());
        return after;
    }

    /**
     * Turns sign-up in the app on or off. The branch must be open, the tier active and the product one an individual
     * can open now, in the institution's base currency.
     */
    @Transactional
    public ChannelSettingsDtos.Settings updateOnboarding(ChannelSettingsDtos.OnboardingUpdate request) {
        ChannelSettings current = load();
        ConcurrentModificationException.assertVersion(request.version(), current.getVersion());
        boolean enabled = request.enabled();
        if (enabled && (request.branchId() == null || request.tierCode() == null || request.productId() == null)) {
            throw new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION,
                    "Sign-up needs the branch new customers belong to, their KYC tier and their first product.");
        }
        if (request.branchId() != null && !branchService.getForInternalUse(request.branchId()).isActive()) {
            throw new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION, "The branch is not open.");
        }
        String tierCode = request.tierCode() == null ? null : request.tierCode().trim().toUpperCase(Locale.ROOT);
        if (tierCode != null) {
            tierService.requireActiveRank(tierCode);
        }
        if (request.productId() != null) {
            ProductTerms terms = productService.termsForNewAccount(request.productId());
            if (!SIGN_UP_PRODUCT_TYPES.contains(terms.productType())
                    || !terms.currency().equals(tenantService.getCurrent().baseCurrency())) {
                throw new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION, "The first account must be a"
                        + " savings or current account in the institution's base currency.");
            }
        }
        ChannelSettingsDtos.Settings before = toResponse(current);
        current.updateOnboarding(enabled, request.branchId(), tierCode, request.productId(), request.minimumAge(),
                clock.instant(), CurrentActor.currentActorId().orElse(null));
        settings.saveAndFlush(current);
        ChannelSettingsDtos.Settings after = toResponse(current);
        auditService.record(AuditEvent.builder("CUSTOMER_SIGN_UP_SETTINGS_UPDATED", RESOURCE)
                .before(before.onboarding())
                .after(after.onboarding())
                .build());
        return after;
    }

    /**
     * The defaults for an institution that has none. Idempotent.
     */
    @Transactional
    public void provisionDefaults() {
        UUID tenantId = TenantContext.requireTenantId();
        if (!settings.existsById(tenantId)) {
            settings.saveAndFlush(new ChannelSettings(tenantId, new BigDecimal("5000"), new BigDecimal("10000"), 24,
                    new BigDecimal("1000"), 24, new BigDecimal("1000"), clock.instant()));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    ChannelSettings current() {
        return load();
    }

    private ChannelSettings load() {
        UUID tenantId = TenantContext.requireTenantId();
        return settings.findById(tenantId).orElseGet(() -> {
            provisionDefaults();
            return settings.findById(tenantId).orElseThrow();
        });
    }

    private ChannelSettingsDtos.Settings toResponse(ChannelSettings value) {
        String currency = tenantService.getCurrent().baseCurrency();
        return new ChannelSettingsDtos.Settings(currency, currencies.present(value.getMaxTransferAmount(), currency),
                currencies.present(value.getDailyTransferLimit(), currency), value.getBeneficiaryCooldownHours(),
                currencies.present(value.getCooldownMaxAmount(), currency), value.getNewDeviceCooldownHours(),
                currencies.present(value.getNewDeviceMaxAmount(), currency), onboarding(value), value.getUpdatedAt(),
                value.getVersion());
    }

    private ChannelSettingsDtos.Onboarding onboarding(ChannelSettings value) {
        String branchName = value.getOnboardingBranchId() == null ? null
                : branchService.getForInternalUse(value.getOnboardingBranchId()).name();
        String productName = value.getOnboardingProductId() == null ? null
                : productService.get(value.getOnboardingProductId()).name();
        return new ChannelSettingsDtos.Onboarding(value.isSelfOnboardingEnabled(), value.getOnboardingBranchId(),
                branchName, value.getOnboardingTierCode(), value.getOnboardingProductId(), productName,
                value.getOnboardingMinAge());
    }
}
