package com.company.banking.loan.schedule;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Arrears arithmetic for end-of-day. Pure.
 * <ul>
 *   <li><b>Days past due:</b> days since the due date of the oldest installment still owing anything (0 on the due
 *   date itself and when nothing is overdue).</li>
 *   <li><b>Penalty:</b> simple interest on the overdue principal and interest at the penalty rate, actual/365, for
 *   each day after the due date plus the grace days. Kept exact; the caller rounds the cumulative total.</li>
 *   <li><b>Provision:</b> the band's rate of the principal outstanding, rounded half up to the minor unit.</li>
 *   <li><b>Band:</b> the band with the highest minimum days not above the days past due.</li>
 * </ul>
 */
public final class LoanArrears {

    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * An installment's due date and what it still owes (principal, interest and penalties).
     */
    public record Owing(LocalDate dueDate, BigDecimal outstanding) {
    }

    /**
     * A delinquency band as configured: it applies from {@code minDays} days past due.
     */
    public record Band(String code, int minDays, BigDecimal provisionRate, boolean suspendAccrual) {
    }

    private LoanArrears() {
    }

    public static int daysPastDue(List<Owing> installments, LocalDate date) {
        return installments.stream()
                .filter(installment -> installment.outstanding().signum() > 0
                        && !installment.dueDate().isAfter(date))
                .map(Owing::dueDate)
                .min(Comparator.naturalOrder())
                .map(oldest -> Math.toIntExact(ChronoUnit.DAYS.between(oldest, date)))
                .orElse(0);
    }

    /**
     * Penalty days of an installment in {@code (from, to]}: the days after its due date plus grace.
     */
    public static long penaltyDays(LocalDate dueDate, int graceDays, LocalDate from, LocalDate to) {
        LocalDate firstPenaltyDay = dueDate.plusDays(graceDays + 1L);
        LocalDate start = from.plusDays(1).isAfter(firstPenaltyDay) ? from.plusDays(1) : firstPenaltyDay;
        return start.isAfter(to) ? 0 : ChronoUnit.DAYS.between(start, to) + 1;
    }

    /**
     * @param annualRatePercent e.g. {@code 36} for 36% a year
     */
    public static BigDecimal penalty(BigDecimal overdue, BigDecimal annualRatePercent, long days) {
        if (overdue.signum() <= 0 || annualRatePercent.signum() <= 0 || days <= 0) {
            return BigDecimal.ZERO;
        }
        return overdue.multiply(annualRatePercent, PRECISION).multiply(BigDecimal.valueOf(days), PRECISION)
                .divide(HUNDRED.multiply(DAYS_IN_YEAR), PRECISION);
    }

    public static BigDecimal provision(BigDecimal principalOutstanding, BigDecimal ratePercent, int minorUnits) {
        return principalOutstanding.multiply(ratePercent).divide(HUNDRED, PRECISION)
                .setScale(minorUnits, RoundingMode.HALF_UP);
    }

    /**
     * @param bands the institution's bands; one of them starts at 0 days
     */
    public static Band bandFor(List<Band> bands, int daysPastDue) {
        return bands.stream()
                .filter(band -> band.minDays() <= daysPastDue)
                .max(Comparator.comparingInt(Band::minDays))
                .orElseThrow(() -> new IllegalArgumentException("No delinquency band starts at 0 days"));
    }
}
