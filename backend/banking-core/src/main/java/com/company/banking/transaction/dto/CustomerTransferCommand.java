package com.company.banking.transaction.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A transfer a customer makes in the app, from one of their accounts to any account of the institution.
 */
public record CustomerTransferCommand(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String narration) {
}
