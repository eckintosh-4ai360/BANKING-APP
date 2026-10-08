package com.company.banking.product.dto;

import java.util.UUID;

public record ProductRef(UUID id, String code, String name, String productType) {
}
