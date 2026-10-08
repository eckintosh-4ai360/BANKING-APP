package com.company.banking.ledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One GL account in the trial balance. Header rows carry the totals of everything below them.
 *
 * @param debitBalance  closing balance when on the debit side, otherwise zero
 * @param creditBalance closing balance when on the credit side, otherwise zero
 */
public record TrialBalanceRow(
        UUID chartOfAccountId,
        UUID parentId,
        String code,
        String name,
        String accountClass,
        boolean header,
        int level,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        BigDecimal debitBalance,
        BigDecimal creditBalance) {
}
