package com.company.banking.product.entity;

import com.company.banking.product.model.ChargeCalculation;
import com.company.banking.product.model.ChargeEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * A charge of a product version. Written and replaced only while the version is a draft (the database refuses
 * anything else).
 */
@Getter
@Entity
@Table(name = "product_charge")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCharge implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "product_version_id", nullable = false, updatable = false)
    private UUID productVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_event", nullable = false, updatable = false, length = 20)
    private ChargeEvent chargeEvent;

    @Column(name = "name", nullable = false, updatable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation", nullable = false, updatable = false, length = 10)
    private ChargeCalculation calculation;

    @Column(name = "flat_amount", updatable = false, precision = 19, scale = 4)
    private BigDecimal flatAmount;

    @Column(name = "rate", updatable = false, precision = 9, scale = 6)
    private BigDecimal rate;

    @Column(name = "min_amount", updatable = false, precision = 19, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", updatable = false, precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newEntity = true;

    public ProductCharge(UUID id, UUID tenantId, UUID productVersionId, ChargeEvent chargeEvent, String name,
                         ChargeCalculation calculation, BigDecimal flatAmount, BigDecimal rate, BigDecimal minAmount,
                         BigDecimal maxAmount) {
        this.id = id;
        this.tenantId = tenantId;
        this.productVersionId = productVersionId;
        this.chargeEvent = chargeEvent;
        this.name = name;
        this.calculation = calculation;
        this.flatAmount = flatAmount;
        this.rate = rate;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newEntity = false;
    }
}
