package com.company.banking.ledger.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AccountingPeriodResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        Instant closedAt,
        UUID closedBy) {
}
