package com.company.banking.ledger.dto;

import java.util.UUID;

public record ChartOfAccountResponse(
        UUID id,
        String code,
        String name,
        String accountClass,
        String normalSide,
        UUID parentId,
        boolean header,
        boolean manualPostingAllowed,
        String systemCode,
        String status,
        Long version) {
}
