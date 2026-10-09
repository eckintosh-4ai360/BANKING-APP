package com.company.banking.teller.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A vault's or drawer's cash at the end of a business date.
 *
 * @param ledgerBalance  cash the ledger says it holds
 * @param countedBalance cash last counted in it (the closing count of the drawer's last session); null for vaults
 * @param difference     counted minus ledger; anything but zero is a {@code BREAK}
 * @param status         {@code MATCHED}, {@code BREAK} or {@code NOT_COUNTED}
 */
public record CashPositionResponse(LocalDate businessDate, UUID cashPointId, String cashPointType, UUID branchId,
                                   String currency, BigDecimal ledgerBalance, BigDecimal countedBalance,
                                   UUID tellerSessionId, Instant countedAt, BigDecimal difference, String status) {
}
