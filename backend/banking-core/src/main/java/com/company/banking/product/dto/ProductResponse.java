package com.company.banking.product.dto;

import java.util.List;
import java.util.UUID;

/**
 * @param currentVersion the published terms offered to new accounts (null until the first version is published)
 * @param versions       every version, newest first
 */
public record ProductResponse(
        UUID id,
        String code,
        String name,
        String productType,
        String description,
        String status,
        ProductVersionResponse currentVersion,
        List<ProductVersionResponse> versions,
        Long version) {
}
