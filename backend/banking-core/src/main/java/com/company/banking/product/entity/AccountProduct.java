package com.company.banking.product.entity;

import com.company.banking.common.persistence.AuditableEntity;
import com.company.banking.product.model.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A deposit product. Its terms live in versions; {@code currentVersionId} is the published version offered to new
 * accounts.
 */
@Getter
@Entity
@Table(name = "account_product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountProduct extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, updatable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, updatable = false, length = 20)
    private ProductType productType;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ProductStatus status;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    public AccountProduct(UUID id, UUID tenantId, String code, String name, ProductType productType,
                          String description) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.productType = productType;
        this.description = description;
        this.status = ProductStatus.ACTIVE;
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void publish(UUID versionId) {
        this.currentVersionId = versionId;
    }

    public void changeStatus(ProductStatus status) {
        this.status = status;
    }
}
