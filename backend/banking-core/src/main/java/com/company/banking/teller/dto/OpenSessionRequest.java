package com.company.banking.teller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * @param openingCount optional count of the cash found in the drawer; it must match the drawer's ledger balance
 */
public record OpenSessionRequest(@NotNull UUID drawerId, @Valid CashCountRequest openingCount) {
}
