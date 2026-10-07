package com.company.banking.customer.dto;

import java.util.UUID;

public record RevealedIdentification(UUID id, String idTypeCode, String idNumber) {

    @Override
    public String toString() {
        return "RevealedIdentification[id=" + id + ", idNumber=***]";
    }
}
