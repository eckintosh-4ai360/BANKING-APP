package com.company.banking.teller.dto;

import java.util.UUID;

/**
 * The drawer a cash transaction posts to: locked (shared) for the rest of the caller's transaction, so its session
 * cannot close underneath it.
 */
public record CashDrawerRef(UUID drawerId, UUID sessionId, String code, UUID ledgerAccountId, UUID branchId,
                            String currency, UUID tellerId) {
}
