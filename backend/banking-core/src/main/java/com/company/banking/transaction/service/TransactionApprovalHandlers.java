package com.company.banking.transaction.service;

import com.company.banking.approval.model.ApprovalType;
import com.company.banking.approval.service.ApprovalHandler;
import com.company.banking.approval.service.ApprovalHandler.ApprovedAction;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connects approved requests to the transaction service: withdrawals and transfers above a threshold, and
 * reversals.
 */
@Configuration
class TransactionApprovalHandlers {

    @Bean
    ApprovalHandler cashWithdrawalApprovalHandler(TransactionService transactionService) {
        return handler(ApprovalType.CASH_WITHDRAWAL, transactionService::executeApprovedWithdrawal);
    }

    @Bean
    ApprovalHandler transferApprovalHandler(TransactionService transactionService) {
        return handler(ApprovalType.TRANSFER, transactionService::executeApprovedTransfer);
    }

    @Bean
    ApprovalHandler transactionReversalApprovalHandler(TransactionService transactionService) {
        return handler(ApprovalType.TRANSACTION_REVERSAL, transactionService::executeApprovedReversal);
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
