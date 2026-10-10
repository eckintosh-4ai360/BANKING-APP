package com.company.banking.channel.service;

import com.company.banking.channel.dto.BankingDtos;
import com.company.banking.channel.dto.ProductDtos;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.loan.dto.LoanDtos;
import com.company.banking.loan.service.LoanService;
import com.company.banking.susu.dto.SusuDtos;
import com.company.banking.susu.service.SusuPlanService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The signed-in customer's loans and susu plans. The loan and susu services serve a customer only their own; this
 * service shows them what they owe and pay, never the institution's internal view (provisions, delinquency bands,
 * accrual status, staff).
 */
@Service
@RequiredArgsConstructor
public class CustomerProductsService {

    private final LoanService loanService;
    private final SusuPlanService susuPlanService;
    private final PinService pinService;

    public List<ProductDtos.Loan> loans() {
        CustomerAuthService.requireCustomer();
        return loanService.customerLoans().stream().map(CustomerProductsService::toLoan).toList();
    }

    public ProductDtos.LoanDetail loan(UUID loanId) {
        CustomerAuthService.requireCustomer();
        LoanDtos.LoanDetail detail = loanService.get(loanId);
        return new ProductDtos.LoanDetail(toLoan(detail.loan()),
                detail.schedule().stream().map(CustomerProductsService::toInstallment).toList(),
                detail.repayments().stream().map(CustomerProductsService::toRepayment).toList(),
                detail.payoff() == null ? null
                        : new ProductDtos.Payoff(detail.payoff().asOf(), detail.payoff().total()));
    }

    /**
     * Repays from the loan's repayment account (one of the customer's), confirmed with the PIN.
     */
    public ProductDtos.RepaymentReceipt repay(String idempotencyKey, UUID loanId, BankingDtos.LoanRepayment request) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        loanService.get(loanId);
        pinService.requireForPayment(actor.id(), request.pin());
        LoanDtos.RepaymentReceipt receipt = loanService.repay(idempotencyKey, loanId,
                new LoanDtos.Repay(request.amount(), "ACCOUNT", null, null)).response();
        return new ProductDtos.RepaymentReceipt(toRepayment(receipt.repayment()), toLoan(receipt.loan()),
                receipt.settled());
    }

    public List<ProductDtos.SusuPlan> susuPlans() {
        CustomerAuthService.requireCustomer();
        return susuPlanService.customerPlans().stream().map(CustomerProductsService::toPlan).toList();
    }

    public ProductDtos.SusuPlanDetail susuPlan(UUID planId) {
        CustomerAuthService.requireCustomer();
        SusuDtos.PlanDetail detail = susuPlanService.get(planId);
        return new ProductDtos.SusuPlanDetail(toPlan(detail.plan()),
                detail.contributions().stream().map(contribution -> new ProductDtos.Contribution(
                        contribution.sequenceNo(), contribution.cycleNo(), contribution.dueDate(),
                        contribution.amount(), contribution.status(), contribution.paidAt())).toList(),
                detail.commissions().stream().map(commission -> new ProductDtos.Commission(commission.cycleNo(),
                        commission.amountCharged(), commission.businessDate())).toList());
    }

    private static ProductDtos.Loan toLoan(LoanDtos.Loan loan) {
        return new ProductDtos.Loan(loan.id(), loan.loanNumber(), loan.productName(), loan.currency(),
                loan.principal(), loan.annualRate(), loan.repaymentFrequency(), loan.installments(),
                loan.disbursementDate(), loan.maturityDate(), loan.status(), loan.daysPastDue(),
                loan.principalOutstanding(), loan.interestReceivable(), loan.penaltyReceivable(), loan.arrears(),
                loan.nextDueDate(), loan.nextDueAmount(), loan.repaymentAccountId(), loan.closedOn());
    }

    private static ProductDtos.Installment toInstallment(LoanDtos.Installment installment) {
        return new ProductDtos.Installment(installment.number(), installment.dueDate(), installment.principalDue(),
                installment.interestDue(), installment.penaltyDue(),
                installment.principalPaid().add(installment.interestPaid()).add(installment.penaltyPaid()),
                installment.outstanding(), installment.paidOn(), installment.status());
    }

    private static ProductDtos.Repayment toRepayment(LoanDtos.Repayment repayment) {
        return new ProductDtos.Repayment(repayment.id(), repayment.transactionId(), repayment.source(),
                repayment.amount(), repayment.principal(), repayment.interest(), repayment.penalty(), repayment.fee(),
                repayment.businessDate(), repayment.createdAt());
    }

    private static ProductDtos.SusuPlan toPlan(SusuDtos.Plan plan) {
        return new ProductDtos.SusuPlan(plan.id(), plan.planNumber(), plan.accountId(), plan.frequencyCode(),
                plan.contributionAmount(), plan.currency(), plan.cycleLength(), plan.startDate(), plan.endDate(),
                plan.targetAmount(), plan.status(), plan.currentCycle(), plan.paid(), plan.missed(), plan.arrears(),
                plan.nextDue(), plan.totalPaid());
    }
}
