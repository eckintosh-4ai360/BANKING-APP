package com.company.banking.teller.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param balance cash held, from the ledger
 */
public record VaultResponse(UUID id, UUID branchId, String currency, String name, String status, BigDecimal balance,
                            Long version) {
}
