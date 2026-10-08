package com.company.banking.ledger.dto;

import java.util.UUID;

/**
 * A GL account as seen by other modules (e.g. product posting rules).
 */
public record GlAccountRef(
        UUID id,
        String code,
        String name,
        String accountClass,
        String normalSide,
        boolean header,
        boolean manualPostingAllowed,
        String systemCode,
        String status) {
}
