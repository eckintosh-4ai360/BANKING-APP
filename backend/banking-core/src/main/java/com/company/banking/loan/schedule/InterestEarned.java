package com.company.banking.loan.schedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Interest a loan has earned by a date, from its schedule. Pure.
 *
 * <p>An installment's interest is earned evenly (by calendar day) over its accrual window: from the due date of the
 * last earlier installment that had interest due (or disbursement) to its own due date. Interest deferred by an
 * interest grace period is therefore earned over the whole deferred stretch, not on one day. Interest of
 * installments due by the date is earned in full; the part of the running window is rounded down to the minor unit,
 * so income is never recognised early and, on each due date, what was earned equals what fell due exactly.
 */
public final class InterestEarned {

    /**
     * One installment's period and interest, as scheduled.
     */
    public record Period(LocalDate fromDate, LocalDate dueDate, BigDecimal interestDue) {
    }

    private InterestEarned() {
    }

    /**
     * @param periods          the schedule, in installment order
     * @param disbursementDate when the loan started earning
     * @param date             the end of the day earned through
     */
    public static BigDecimal through(List<Period> periods, LocalDate disbursementDate, LocalDate date,
                                     int minorUnits) {
        BigDecimal earned = BigDecimal.ZERO.setScale(minorUnits);
        LocalDate windowStart = disbursementDate;
        for (Period period : periods) {
            if (period.interestDue().signum() == 0) {
                continue;
            }
            if (!date.isBefore(period.dueDate())) {
                earned = earned.add(period.interestDue());
            } else if (date.isAfter(windowStart)) {
                long elapsed = ChronoUnit.DAYS.between(windowStart, date);
                long window = ChronoUnit.DAYS.between(windowStart, period.dueDate());
                earned = earned.add(period.interestDue().multiply(BigDecimal.valueOf(elapsed))
                        .divide(BigDecimal.valueOf(window), minorUnits, RoundingMode.DOWN));
                break;
            } else {
                break;
            }
            windowStart = period.dueDate();
        }
        return earned;
    }
}
