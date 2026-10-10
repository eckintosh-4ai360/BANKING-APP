package com.company.banking.channel.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.channel.dto.ChannelSettingsDtos;
import com.company.banking.channel.entity.ChannelSettings;
import com.company.banking.channel.repository.ChannelSettingsRepository;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.tenant.service.TenantService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The institution's limits for transfers from the customer app, in its base currency. A new institution starts with
 * conservative defaults to adjust: 5,000 a transfer, 10,000 a day, and 1,000 a transfer for 24 hours after a
 * beneficiary is added or a new device is trusted.
 */
@Service
@RequiredArgsConstructor
public class ChannelSettingsService {

    private static final String RESOURCE = "CUSTOMER_CHANNEL_SETTINGS";

    private final ChannelSettingsRepository settings;
    private final TenantService tenantService;
    private final CurrencyService currencies;
    private final AuditService auditService;
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
                currencies.present(value.getNewDeviceMaxAmount(), currency), value.getUpdatedAt(),
                value.getVersion());
    }
}
