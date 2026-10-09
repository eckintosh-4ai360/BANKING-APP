package com.company.banking.loan.service;

import com.company.banking.common.error.BankingException;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.loan.dto.LoanDtos;
import com.company.banking.loan.entity.LoanProductVersion;
import com.company.banking.loan.exception.LoanErrorCode;
import com.company.banking.loan.schedule.ScheduleCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What a product version's terms mean for one loan: limits, processing fee, first due date and schedule. Every
 * amount here is computed on the server from the stored terms; nothing is taken from the client.
 */
@Component
@RequiredArgsConstructor
class LoanTerms {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CurrencyService currencies;

    void requireWithinLimits(LoanProductVersion version, BigDecimal amount, int installments) {
        currencies.requireValidAmount(amount, version.getCurrency());
        if (amount.compareTo(version.getMinAmount()) < 0 || amount.compareTo(version.getMaxAmount()) > 0
                || installments < version.getMinInstallments() || installments > version.getMaxInstallments()) {
            throw new BankingException(LoanErrorCode.OUTSIDE_PRODUCT_LIMITS,
                    "The product lends " + version.getCurrency() + " "
                            + currencies.present(version.getMinAmount(), version.getCurrency()).toPlainString()
                            + " to " + currencies.present(version.getMaxAmount(), version.getCurrency())
                            .toPlainString() + " over " + version.getMinInstallments() + " to "
                            + version.getMaxInstallments() + " installments.");
        }
    }

    /**
     * Rate × principal (rounded to the minor unit as the product rounds) plus the flat fee. It must leave something
     * to disburse.
     */
    BigDecimal processingFee(LoanProductVersion version, BigDecimal principal) {
        int minorUnits = minorUnits(version);
        BigDecimal fee = principal.multiply(version.getProcessingFeeRate()).divide(HUNDRED)
                .setScale(minorUnits, rounding(version)).add(version.getProcessingFeeFlat())
                .setScale(minorUnits, RoundingMode.UNNECESSARY);
        if (fee.compareTo(principal) >= 0) {
            throw new BankingException(LoanErrorCode.OUTSIDE_PRODUCT_LIMITS,
                    "The processing fee would take the whole loan.");
        }
        return fee;
    }

    /**
     * A chosen first due date must be after disbursement and at most two repayment periods away (a longer wait is
     * a grace period, which belongs in the product's terms).
     */
    void requireFirstDueDate(LoanProductVersion version, LocalDate disbursement, LocalDate firstDue) {
        if (firstDue != null && (!firstDue.isAfter(disbursement)
                || firstDue.isAfter(version.getRepaymentFrequency().plus(disbursement, 2)))) {
            throw new BankingException(LoanErrorCode.INVALID_FIRST_DUE_DATE);
        }
    }

    ScheduleCalculator.Schedule schedule(LoanProductVersion version, BigDecimal principal, int installments,
                                         LocalDate disbursement, LocalDate firstDue) {
        if (version.getPrincipalGrace() >= installments) {
            throw new BankingException(LoanErrorCode.OUTSIDE_PRODUCT_LIMITS,
                    "The loan needs more installments than the product's principal grace period.");
        }
        return ScheduleCalculator.calculate(new ScheduleCalculator.Terms(principal, version.getAnnualRate(),
                version.getInterestMethod(), version.getRepaymentFrequency(), version.getDayCount(), installments,
                disbursement, firstDue, version.getPrincipalGrace(), version.getInterestGrace(), minorUnits(version),
                rounding(version)));
    }

    LoanDtos.SchedulePreview preview(LoanProductVersion version, BigDecimal principal, int installments,
                                     LocalDate disbursement, LocalDate firstDue) {
        requireFirstDueDate(version, disbursement, firstDue);
        ScheduleCalculator.Schedule schedule = schedule(version, principal, installments, disbursement, firstDue);
        BigDecimal fee = processingFee(version, principal);
        String currency = version.getCurrency();
        return new LoanDtos.SchedulePreview(present(principal, currency), present(fee, currency),
                present(principal.subtract(fee), currency), present(schedule.totalInterest(), currency),
                present(schedule.totalPrincipal().add(schedule.totalInterest()), currency),
                schedule.installmentAmount() == null ? null : present(schedule.installmentAmount(), currency),
                disbursement, schedule.maturityDate(), schedule.installments().stream()
                .map(line -> new LoanDtos.ScheduleLine(line.number(), line.fromDate(), line.dueDate(),
                        present(line.principal(), currency), present(line.interest(), currency),
                        present(line.total(), currency), present(line.outstandingAfter(), currency)))
                .toList());
    }

    int minorUnits(LoanProductVersion version) {
        return currencies.require(version.getCurrency()).minorUnits();
    }

    static RoundingMode rounding(LoanProductVersion version) {
        return RoundingMode.valueOf(version.getRoundingMode());
    }

    private BigDecimal present(BigDecimal amount, String currency) {
        return currencies.present(amount, currency);
    }
}
