package com.company.banking.loan.schedule;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Day-count conventions: the fraction of a year between two dates.
 */
public enum LoanDayCount {

    /** Actual days / 365. */
    ACTUAL_365F,

    /** Actual days / 360. */
    ACTUAL_360,

    /**
     * 30/360 (bond basis): every month counts 30 days; a start on the 31st counts as the 30th, and an end on the 31st
     * counts as the 30th when the start is the 30th or 31st.
     */
    THIRTY_360;

    static final MathContext PRECISION = MathContext.DECIMAL128;

    public BigDecimal yearFraction(LocalDate from, LocalDate to) {
        return switch (this) {
            case ACTUAL_365F -> BigDecimal.valueOf(ChronoUnit.DAYS.between(from, to))
                    .divide(BigDecimal.valueOf(365), PRECISION);
            case ACTUAL_360 -> BigDecimal.valueOf(ChronoUnit.DAYS.between(from, to))
                    .divide(BigDecimal.valueOf(360), PRECISION);
            case THIRTY_360 -> BigDecimal.valueOf(days360(from, to)).divide(BigDecimal.valueOf(360), PRECISION);
        };
    }

    static long days360(LocalDate from, LocalDate to) {
        int d1 = Math.min(from.getDayOfMonth(), 30);
        int d2 = to.getDayOfMonth() == 31 && d1 >= 30 ? 30 : to.getDayOfMonth();
        return 360L * (to.getYear() - from.getYear()) + 30L * (to.getMonthValue() - from.getMonthValue()) + (d2 - d1);
    }
}
