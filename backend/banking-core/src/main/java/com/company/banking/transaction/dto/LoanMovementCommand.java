package com.company.banking.transaction.dto;

import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.transaction.model.TransactionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A loan disbursement or repayment as the loan module hands it to the transaction module, which adds the
 * customer's side with the usual checks.
 *
 * @param type      {@code LOAN_DISBURSEMENT} (credits {@code accountId}) or {@code LOAN_REPAYMENT} (debits
 *                  {@code accountId}, or takes cash at the caller's till when it is null)
 * @param fee       charged on a disbursement: debited from the account, credited to {@code feeGlId}
 * @param loanLines the loan's side of the journal (e.g. Dr loan principal; Cr principal, interest, penalty)
 * @param branchId  the loan's branch
 */
public record LoanMovementCommand(TransactionType type, UUID accountId, String currency, BigDecimal amount,
                                  BigDecimal fee, UUID feeGlId, List<PostingLine> loanLines, UUID branchId,
                                  String narration, String externalReference, String idempotencyKey) {
}
