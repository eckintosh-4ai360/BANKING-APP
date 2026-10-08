package com.company.banking.transaction.dto;

import com.company.banking.approval.dto.ApprovalResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The result of asking to move money: either it was posted (the transaction and the balances it left behind) or it
 * is above an approval threshold and waits for a checker (the approval request). A retried request (same
 * Idempotency-Key) gets this same response again.
 *
 * @param outcome {@code POSTED} or {@code PENDING_APPROVAL}
 */
public record MovementResponse(String outcome, TransactionResponse transaction, List<BalanceAfter> balances,
                               ApprovalResponse approval) {

    public static final String POSTED = "POSTED";
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";

    public static MovementResponse posted(TransactionResponse transaction, List<BalanceAfter> balances) {
        return new MovementResponse(POSTED, transaction, List.copyOf(balances), null);
    }

    public static MovementResponse pending(ApprovalResponse approval) {
        return new MovementResponse(PENDING_APPROVAL, null, List.of(), approval);
    }

    public boolean isPosted() {
        return POSTED.equals(outcome);
    }

    public record BalanceAfter(UUID accountId, String accountNumber, BigDecimal ledgerBalance,
                               BigDecimal availableBalance) {
    }
}
