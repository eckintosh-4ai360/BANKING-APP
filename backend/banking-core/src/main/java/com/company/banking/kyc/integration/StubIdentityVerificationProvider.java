package com.company.banking.kyc.integration;

import com.company.banking.customer.dto.IdentityVerificationSubject;
import com.company.banking.kyc.spi.IdentityVerificationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Deterministic stand-in for local development and tests: numbers ending in 9 do not match, everything else
 * matches. Enabled only with {@code banking.kyc.identity-verification-provider=stub}; deployed environments refuse
 * that setting.
 */
@Component
@ConditionalOnProperty(name = "banking.kyc.identity-verification-provider", havingValue = "stub")
public class StubIdentityVerificationProvider implements IdentityVerificationProvider {

    @Override
    public String name() {
        return "STUB";
    }

    @Override
    public boolean supports(String idTypeCode, String issuingCountry) {
        return true;
    }

    @Override
    public Result verify(IdentityVerificationSubject subject) {
        String reference = "STUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        if (subject.idNumber().endsWith("9")) {
            return new Result(Outcome.NO_MATCH, new BigDecimal("12.00"), reference, "Identity data did not match");
        }
        return new Result(Outcome.MATCH, new BigDecimal("98.50"), reference, "Identity confirmed");
    }
}
