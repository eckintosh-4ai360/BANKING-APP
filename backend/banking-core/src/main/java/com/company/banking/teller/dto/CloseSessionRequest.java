package com.company.banking.teller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A blind count: the teller counts first, then the system compares with the drawer's ledger balance.
 */
public record CloseSessionRequest(@NotNull @Valid CashCountRequest count, @Size(max = 300) String note,
                                  @NotNull Long version) {
}
