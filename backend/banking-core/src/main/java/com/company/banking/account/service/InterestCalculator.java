package com.company.banking.account.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deposit interest arithmetic. Exact and pure: BigDecimal throughout, daily interest kept to 8 decimals, and amounts
 * that move money rounded half-even to the currency's minor unit (no systematic bias across many accounts).
 */
public final class InterestCalculator {

    /** Precision of daily interest before it is rounded for posting. */
    public static final int EXACT_SCALE = 8;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Set<Month> QUARTER_ENDS = Set.of(Month.MARCH, Month.JUNE, Month.SEPTEMBER, Month.DECEMBER);

    private InterestCalculator() {
    }

    public static int yearDays(String dayCount) {
        return switch (dayCount) {
            case "ACTUAL_365F" -> 365;
            case "ACTUAL_360", "THIRTY_360" -> 360;
            default -> throw new IllegalArgumentException("Unknown day count " + dayCount);
        };
    }

    /**
     * How many days of interest a calendar day earns. Actual day counts: one. 30/360: every month counts 30 days, so
     * the 31st earns nothing and the last day of February earns the days February lacks (3 in a common year, 2 in a
     * leap year).
     */
    public static int dayWeight(LocalDate date, String dayCount) {
        if (!"THIRTY_360".equals(dayCount)) {
            return 1;
        }
        if (date.getDayOfMonth() == 31) {
            return 0;
        }
        if (date.getMonth() == Month.FEBRUARY && date.getDayOfMonth() == date.lengthOfMonth()) {
            return 30 - date.getDayOfMonth() + 1;
        }
        return 1;
    }

    /**
     * One day's interest on a closing balance at an annual percentage rate. Nothing on a zero or negative balance.
     */
    public static BigDecimal dailyInterest(BigDecimal balance, BigDecimal annualRatePercent, int weight,
                                           int yearDays) {
        if (balance.signum() <= 0 || annualRatePercent.signum() <= 0 || weight == 0) {
            return BigDecimal.ZERO.setScale(EXACT_SCALE);
        }
        return balance.multiply(annualRatePercent).multiply(BigDecimal.valueOf(weight))
                .divide(HUNDRED.multiply(BigDecimal.valueOf(yearDays)), EXACT_SCALE, RoundingMode.HALF_EVEN);
    }

    /**
     * Interest on the lowest balance of a period (the minimum-balance method).
     */
    public static BigDecimal minimumBalanceInterest(BigDecimal minimumBalance, BigDecimal annualRatePercent,
                                                    int weightedDays, int yearDays) {
        return dailyInterest(minimumBalance, annualRatePercent, weightedDays, yearDays);
    }

    public static BigDecimal toMinorUnits(BigDecimal exact, int minorUnits) {
        return exact.setScale(minorUnits, RoundingMode.HALF_EVEN);
    }

    /**
     * What to post to the GL when the running exact total moves from {@code before} to {@code after}: the change of
     * its rounded value. Posting these increments keeps the GL equal to the rounded running total, with no drift.
     */
    public static BigDecimal glIncrement(BigDecimal before, BigDecimal after, int minorUnits) {
        return toMinorUnits(after, minorUnits).subtract(toMinorUnits(before, minorUnits));
    }

    /**
     * Interest period ends within {@code [from, to]} for a posting frequency.
     */
    public static List<LocalDate> periodEnds(String frequency, LocalDate from, LocalDate to) {
        List<LocalDate> ends = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            boolean monthEnd = day.getDayOfMonth() == day.lengthOfMonth();
            boolean end = switch (frequency) {
                case "MONTHLY" -> monthEnd;
                case "QUARTERLY" -> monthEnd && QUARTER_ENDS.contains(day.getMonth());
                case "ANNUALLY" -> monthEnd && day.getMonth() == Month.DECEMBER;
                case "NONE" -> false;
                default -> throw new IllegalArgumentException("Unknown posting frequency " + frequency);
            };
            if (end) {
                ends.add(day);
            }
        }
        return ends;
    }
}
