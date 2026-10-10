package com.company.banking.account.dto;

import java.util.UUID;

/**
 * An account as a payer sees it: enough to send money to it, nothing about its balance or holders.
 */
public record TransferDestination(UUID accountId, String accountNumber, String title, String currency,
                                  String status) {
}
