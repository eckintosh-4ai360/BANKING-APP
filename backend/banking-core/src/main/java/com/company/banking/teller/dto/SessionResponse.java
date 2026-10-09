package com.company.banking.teller.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param currentBalance  the drawer's ledger balance now (what the teller should hold)
 * @param difference      counted minus expected; negative is a shortage
 */
public record SessionResponse(
        UUID id,
        UUID drawerId,
        String drawerCode,
        UUID branchId,
        UUID tellerId,
        LocalDate businessDate,
        String status,
        String currency,
        BigDecimal openingBalance,
        BigDecimal currentBalance,
        BigDecimal expectedClosingBalance,
        BigDecimal countedBalance,
        BigDecimal difference,
        Instant openedAt,
        Instant closedAt,
        UUID supervisorId,
        String closeNote,
        Long version) {
}
