package com.company.banking.kyc.spi;

import com.company.banking.customer.dto.IdentityVerificationSubject;

import java.math.BigDecimal;

/**
 * Port to an identity-verification service (e.g. Ghana Card verification through an approved provider). Adapters
 * live in their own package and are enabled by configuration only once a provider contract exists.
 *
 * <p>Implementations must not log the subject and must treat timeouts as {@link Outcome#ERROR}, never as a match.
 */
public interface IdentityVerificationProvider {

    String name();

    boolean supports(String idTypeCode, String issuingCountry);

    Result verify(IdentityVerificationSubject subject);

    enum Outcome { MATCH, NO_MATCH, NOT_FOUND, ERROR }

    record Result(Outcome outcome, BigDecimal score, String reference, String message) {
    }
}
