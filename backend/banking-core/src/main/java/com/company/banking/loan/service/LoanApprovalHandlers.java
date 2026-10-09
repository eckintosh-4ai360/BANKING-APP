package com.company.banking.loan.service;

import com.company.banking.approval.model.ApprovalType;
import com.company.banking.approval.service.ApprovalHandler;
import com.company.banking.approval.service.ApprovalHandler.ApprovedAction;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connects approved loan restructures and write-offs to the loan service. The checker approving them is someone
 * other than the maker (the approval module and the database both enforce it).
 */
@Configuration
class LoanApprovalHandlers {

    @Bean
    ApprovalHandler loanRestructureApprovalHandler(LoanService loanService) {
        return handler(ApprovalType.LOAN_RESTRUCTURE, loanService::executeRestructure);
    }

    @Bean
    ApprovalHandler loanWriteOffApprovalHandler(LoanService loanService) {
        return handler(ApprovalType.LOAN_WRITE_OFF, loanService::executeWriteOff);
    }

    private static ApprovalHandler handler(ApprovalType type, Function<ApprovedAction, UUID> action) {
        return new ApprovalHandler() {
            @Override
            public ApprovalType type() {
                return type;
            }

            @Override
            public UUID execute(ApprovedAction approved) {
                return action.apply(approved);
            }
        };
    }
}
