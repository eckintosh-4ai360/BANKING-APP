package com.company.banking.manualjournal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A manual journal on GL accounts that allow manual posting (adjustments, corrections, capital injections). It is
 * posted only after a second person approves it.
 *
 * @param branchId  the branch the journal belongs to
 * @param valueDate defaults to the business date; may lie in the past within an open period
 */
public record ManualJournalRequest(
        @NotNull UUID branchId,
        LocalDate valueDate,
        @NotBlank @Size(max = 250) String description,
        @NotNull @Size(min = 2, max = 50) List<@Valid @NotNull Line> lines) {

    /**
     * @param branchId defaults to the journal's branch
     */
    public record Line(
            @NotNull UUID chartOfAccountId,
            UUID branchId,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotBlank @Pattern(regexp = "^(DEBIT|CREDIT)$") String direction,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
            @Size(max = 200) String narration) {
    }
}
