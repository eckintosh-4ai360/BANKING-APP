package com.company.banking.fieldops.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class RemittanceDtos {

    private RemittanceDtos() {
    }

    /**
     * Cash a field officer hands to the teller, as the teller counted it.
     */
    public record Remit(
            @NotNull UUID officerId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Size(max = 300) String note) {
    }

    /**
     * @param officerCashAfter what the officer still carries
     */
    public record Remittance(UUID id, String reference, UUID officerId, UUID tellerId, UUID drawerId,
                             String drawerCode, UUID branchId, BigDecimal amount, String currency,
                             LocalDate businessDate, String note, Instant createdAt, BigDecimal officerCashAfter) {
    }
}
