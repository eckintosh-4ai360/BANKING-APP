package com.company.banking.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An account with its live balance. Balances come from the ledger and are authoritative; clients display them but
 * never compute with them.
 */
public record AccountResponse(
        UUID id,
        String accountNumber,
        String title,
        UUID customerId,
        UUID productId,
        String productCode,
        String productName,
        String productType,
        UUID productVersionId,
        UUID branchId,
        String currency,
        String status,
        String statusReason,
        String ownershipType,
        List<AccountHolderResponse> holders,
        BigDecimal ledgerBalance,
        BigDecimal holdAmount,
        BigDecimal availableBalance,
        BigDecimal overdraftLimit,
        LocalDate openedOn,
        Instant activatedAt,
        LocalDate closedOn,
        Instant lastActivityAt,
        Long version) {

    public record AccountHolderResponse(UUID customerId, String customerNumber, String displayName, String role) {
    }
}
