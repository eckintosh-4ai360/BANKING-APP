package com.company.banking.teller.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param balance   cash the drawer should hold, from the ledger
 * @param sessionId the open or balancing session on the drawer, if any
 */
public record DrawerResponse(UUID id, UUID branchId, String currency, String code, String name, String status,
                             BigDecimal balance, UUID sessionId, UUID tellerId, Long version) {
}
