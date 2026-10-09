package com.company.banking.loan.schedule;

import java.time.LocalDate;

/**
 * How often installments fall due. Every due date is counted from the first one (the 31st stays the last day of
 * shorter months instead of drifting to the 28th).
 */
public enum RepaymentFrequency {
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    QUARTERLY;

    /** The {@code k}-th period after {@code from} (k = 0 is {@code from}). */
    public LocalDate plus(LocalDate from, long k) {
        return switch (this) {
            case DAILY -> from.plusDays(k);
            case WEEKLY -> from.plusWeeks(k);
            case BIWEEKLY -> from.plusWeeks(2 * k);
            case MONTHLY -> from.plusMonths(k);
            case QUARTERLY -> from.plusMonths(3 * k);
        };
    }
}
