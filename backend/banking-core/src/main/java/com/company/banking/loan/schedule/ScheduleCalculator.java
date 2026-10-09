package com.company.banking.loan.schedule;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Loan repayment schedules. Pure and exact: BigDecimal throughout (34 significant digits), amounts rounded to the
 * currency's minor unit only where money is due.
 *
 * <h2>Definitions</h2>
 * <ul>
 *   <li><b>Due dates:</b> the first due date (one period after disbursement unless given), then one period apart,
 *   each counted from the first.</li>
 *   <li><b>Period rate:</b> annual rate × the period's year fraction under the day count (period = previous due
 *   date, or disbursement, to the due date), so an irregular first period is charged exactly.</li>
 *   <li><b>Exact interest of a period:</b> {@code FLAT}: original principal × period rate;
 *   {@code DECLINING_*}: outstanding principal × period rate.</li>
 *   <li><b>Interest due:</b> the cumulative exact interest is rounded and each installment takes the change, so the
 *   total interest is the rounded exact total (no drift). Interest of the first {@code interestGrace} installments is
 *   not due then; it falls due with the next installment.</li>
 *   <li><b>Principal:</b> none during the first {@code principalGrace} installments. {@code FLAT} and
 *   {@code DECLINING_BALANCE_EQUAL_PRINCIPAL}: principal ÷ amortising installments, rounded down to the minor unit,
 *   the remainder in the last installment. {@code DECLINING_BALANCE_EQUAL_INSTALLMENT}: the installment
 *   {@code A = P / Σₖ Πⱼ≤ₖ (1 + iⱼ)⁻¹} over the amortising periods, rounded; principal = A − interest due; the last
 *   installment repays whatever is left.</li>
 * </ul>
 * Principal therefore always adds up to exactly the amount disbursed.
 */
public final class ScheduleCalculator {

    private static final MathContext PRECISION = LoanDayCount.PRECISION;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ScheduleCalculator() {
    }

    /**
     * @param principal       amount disbursed, in minor units of the currency
     * @param annualRatePercent e.g. {@code 24} for 24% a year
     * @param firstDueDate    null for one period after disbursement
     * @param principalGrace  leading installments without principal (interest only)
     * @param interestGrace   leading installments without anything due (at most {@code principalGrace})
     * @param minorUnits      decimals of the currency (2 for GHS)
     * @param rounding        how money amounts are rounded ({@code HALF_EVEN} by default)
     */
    public record Terms(BigDecimal principal, BigDecimal annualRatePercent, InterestMethod method,
                        RepaymentFrequency frequency, LoanDayCount dayCount, int installments,
                        LocalDate disbursementDate, LocalDate firstDueDate, int principalGrace, int interestGrace,
                        int minorUnits, RoundingMode rounding) {

        public Terms {
            if (principal == null || principal.signum() <= 0 || principal.stripTrailingZeros().scale() > minorUnits) {
                throw new IllegalArgumentException("Principal must be positive, in minor units");
            }
            if (annualRatePercent == null || annualRatePercent.signum() < 0) {
                throw new IllegalArgumentException("The rate must not be negative");
            }
            if (installments < 1 || principalGrace < 0 || interestGrace < 0 || principalGrace >= installments
                    || interestGrace > principalGrace) {
                throw new IllegalArgumentException("Grace periods must leave at least one amortising installment,"
                        + " and interest grace may not exceed principal grace");
            }
            if (firstDueDate != null && !firstDueDate.isAfter(disbursementDate)) {
                throw new IllegalArgumentException("The first due date must be after disbursement");
            }
        }
    }

    /**
     * @param fromDate        start of the period the installment's interest covers
     * @param outstandingAfter principal still owed after the installment is paid
     */
    public record Installment(int number, LocalDate fromDate, LocalDate dueDate, BigDecimal principal,
                              BigDecimal interest, BigDecimal total, BigDecimal outstandingAfter) {
    }

    public record Schedule(List<Installment> installments, BigDecimal totalPrincipal, BigDecimal totalInterest,
                           BigDecimal installmentAmount) {

        public LocalDate maturityDate() {
            return installments.getLast().dueDate();
        }
    }

    public static Schedule calculate(Terms terms) {
        int n = terms.installments();
        LocalDate first = terms.firstDueDate() != null ? terms.firstDueDate()
                : terms.frequency().plus(terms.disbursementDate(), 1);
        LocalDate[] from = new LocalDate[n];
        LocalDate[] due = new LocalDate[n];
        BigDecimal[] rate = new BigDecimal[n];
        BigDecimal annual = terms.annualRatePercent().divide(HUNDRED, PRECISION);
        for (int k = 0; k < n; k++) {
            due[k] = terms.frequency().plus(first, k);
            from[k] = k == 0 ? terms.disbursementDate() : due[k - 1];
            rate[k] = annual.multiply(terms.dayCount().yearFraction(from[k], due[k]), PRECISION);
        }

        BigDecimal principal = terms.principal().setScale(terms.minorUnits(), RoundingMode.UNNECESSARY);
        int amortising = n - terms.principalGrace();
        BigDecimal equalPrincipal = principal.divide(BigDecimal.valueOf(amortising), terms.minorUnits(),
                RoundingMode.DOWN);
        BigDecimal installmentAmount = terms.method() == InterestMethod.DECLINING_BALANCE_EQUAL_INSTALLMENT
                ? annuity(principal, rate, terms.principalGrace(), terms) : null;

        BigDecimal zero = BigDecimal.ZERO.setScale(terms.minorUnits());
        List<Installment> installments = new ArrayList<>(n);
        BigDecimal outstanding = principal;
        BigDecimal cumulativeExact = BigDecimal.ZERO;
        BigDecimal cumulativeDue = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        for (int k = 0; k < n; k++) {
            BigDecimal base = terms.method() == InterestMethod.FLAT ? principal : outstanding;
            cumulativeExact = cumulativeExact.add(base.multiply(rate[k], PRECISION), PRECISION);
            BigDecimal interest = zero;
            if (k >= terms.interestGrace()) {
                BigDecimal rounded = cumulativeExact.setScale(terms.minorUnits(), terms.rounding());
                interest = rounded.subtract(cumulativeDue);
                cumulativeDue = rounded;
            }

            BigDecimal principalDue;
            if (k < terms.principalGrace()) {
                principalDue = zero;
            } else if (k == n - 1) {
                principalDue = outstanding;
            } else if (installmentAmount != null) {
                principalDue = installmentAmount.subtract(interest).max(zero).min(outstanding);
            } else {
                principalDue = equalPrincipal.min(outstanding);
            }
            outstanding = outstanding.subtract(principalDue);
            totalInterest = totalInterest.add(interest);
            installments.add(new Installment(k + 1, from[k], due[k], principalDue, interest,
                    principalDue.add(interest), outstanding));
        }
        return new Schedule(List.copyOf(installments), principal, totalInterest, installmentAmount);
    }

    /**
     * The equal installment that repays {@code principal} over the amortising periods at their own period rates:
     * {@code P / Σₖ Πⱼ≤ₖ (1 + iⱼ)⁻¹}, rounded to the minor unit.
     */
    private static BigDecimal annuity(BigDecimal principal, BigDecimal[] rate, int grace, Terms terms) {
        BigDecimal discount = BigDecimal.ONE;
        BigDecimal sum = BigDecimal.ZERO;
        for (int k = grace; k < rate.length; k++) {
            discount = discount.divide(BigDecimal.ONE.add(rate[k]), PRECISION);
            sum = sum.add(discount, PRECISION);
        }
        return principal.divide(sum, PRECISION).setScale(terms.minorUnits(), terms.rounding());
    }
}
