package com.company.banking.channel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A customer's loans and susu plans as the app shows them: what they owe, pay and have paid. The institution's own
 * view (provisions, delinquency bands, accrual status, who received a payment) is not part of it.
 */
public final class ProductDtos {

    private ProductDtos() {
    }

    /**
     * @param interestDue interest earned and not yet paid
     * @param penaltyDue  penalties charged and not yet paid
     * @param arrears     what is overdue
     */
    public record Loan(UUID id, String loanNumber, String productName, String currency, BigDecimal principal,
                       BigDecimal annualRate, String repaymentFrequency, int installments, LocalDate disbursementDate,
                       LocalDate maturityDate, String status, int daysPastDue, BigDecimal principalOutstanding,
                       BigDecimal interestDue, BigDecimal penaltyDue, BigDecimal arrears, LocalDate nextDueDate,
                       BigDecimal nextDueAmount, UUID repaymentAccountId, LocalDate closedOn) {
    }

    /**
     * @param paid what has been paid towards it (principal, interest and penalty)
     */
    public record Installment(int number, LocalDate dueDate, BigDecimal principalDue, BigDecimal interestDue,
                              BigDecimal penaltyDue, BigDecimal paid, BigDecimal outstanding, LocalDate paidOn,
                              String status) {
    }

    public record Repayment(UUID id, UUID transactionId, String source, BigDecimal amount, BigDecimal principal,
                            BigDecimal interest, BigDecimal penalty, BigDecimal fee, LocalDate businessDate,
                            Instant createdAt) {
    }

    /**
     * What settles the loan today.
     */
    public record Payoff(LocalDate asOf, BigDecimal total) {
    }

    public record LoanDetail(Loan loan, List<Installment> schedule, List<Repayment> repayments, Payoff payoff) {
    }

    /**
     * @param settled the repayment paid the loan off
     */
    public record RepaymentReceipt(Repayment repayment, Loan loan, boolean settled) {
    }

    public record SusuPlan(UUID id, String planNumber, UUID accountId, String frequencyCode,
                           BigDecimal contributionAmount, String currency, int cycleLength, LocalDate startDate,
                           LocalDate endDate, BigDecimal targetAmount, String status, int currentCycle, long paid,
                           long missed, BigDecimal arrears, LocalDate nextDue, BigDecimal totalPaid) {
    }

    public record Contribution(int sequenceNo, int cycleNo, LocalDate dueDate, BigDecimal amount, String status,
                               Instant paidAt) {
    }

    /**
     * The collector's commission for a cycle, as agreed when the plan was opened.
     */
    public record Commission(int cycleNo, BigDecimal amountCharged, LocalDate businessDate) {
    }

    public record SusuPlanDetail(SusuPlan plan, List<Contribution> contributions, List<Commission> commissions) {
    }
}
