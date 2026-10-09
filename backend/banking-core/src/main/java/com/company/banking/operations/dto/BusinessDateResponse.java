package com.company.banking.operations.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * @param nextBusinessDate where end-of-day will move the business date
 * @param workingDays      e.g. {@code ["MONDAY", ..., "FRIDAY"]}
 */
public record BusinessDateResponse(
        LocalDate businessDate,
        LocalDate previousBusinessDate,
        LocalDate nextBusinessDate,
        List<String> workingDays,
        Long calendarVersion) {
}
