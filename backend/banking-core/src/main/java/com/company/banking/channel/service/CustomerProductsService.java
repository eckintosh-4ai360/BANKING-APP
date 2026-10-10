package com.company.banking.channel.service;

import com.company.banking.channel.dto.BankingDtos;
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
 * The signed-in customer's loans and susu plans. The loan and susu services serve a customer only their own.
 */
@Service
@RequiredArgsConstructor
public class CustomerProductsService {

    private final LoanService loanService;
    private final SusuPlanService susuPlanService;
    private final PinService pinService;

    public List<LoanDtos.Loan> loans() {
        CustomerAuthService.requireCustomer();
        return loanService.customerLoans();
    }

    public LoanDtos.LoanDetail loan(UUID loanId) {
        CustomerAuthService.requireCustomer();
        return loanService.get(loanId);
    }

    /**
     * Repays from the loan's repayment account (one of the customer's), confirmed with the PIN.
     */
    public LoanDtos.RepaymentReceipt repay(String idempotencyKey, UUID loanId, BankingDtos.LoanRepayment request) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        loanService.get(loanId);
        pinService.requireForPayment(actor.id(), request.pin());
        return loanService.repay(idempotencyKey, loanId, new LoanDtos.Repay(request.amount(), "ACCOUNT", null, null))
                .response();
    }

    public List<SusuDtos.Plan> susuPlans() {
        CustomerAuthService.requireCustomer();
        return susuPlanService.customerPlans();
    }

    public SusuDtos.PlanDetail susuPlan(UUID planId) {
        CustomerAuthService.requireCustomer();
        return susuPlanService.get(planId);
    }
}
