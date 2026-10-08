package com.company.banking.ledger.dto;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

/**
 * @param branchIds restrict to these branches (null = all branches the caller may see)
 */
public record JournalSearchCriteria(
        LocalDate from,
        LocalDate to,
        String sourceType,
        String sourceReference,
        Collection<UUID> branchIds) {
}
