package com.company.banking.kyc.service;

import com.company.banking.customer.dto.IdentityVerificationSubject;
import com.company.banking.kyc.spi.IdentityVerificationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Chooses the configured provider for a document and shields callers from provider failures: an exception becomes
 * an {@code ERROR} result, never a match.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityVerificationGateway {

    private final List<IdentityVerificationProvider> providers;

    public Optional<IdentityVerificationProvider> providerFor(String idTypeCode, String issuingCountry) {
        return providers.stream().filter(provider -> provider.supports(idTypeCode, issuingCountry)).findFirst();
    }

    public IdentityVerificationProvider.Result verify(IdentityVerificationProvider provider,
                                                      IdentityVerificationSubject subject) {
        try {
            return provider.verify(subject);
        } catch (RuntimeException ex) {
            log.warn("Identity verification provider {} failed: {}", provider.name(), ex.getClass().getSimpleName());
            return new IdentityVerificationProvider.Result(IdentityVerificationProvider.Outcome.ERROR, null, null,
                    "Provider unavailable");
        }
    }
}
