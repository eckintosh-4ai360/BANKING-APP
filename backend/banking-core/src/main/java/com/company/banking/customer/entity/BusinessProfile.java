package com.company.banking.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Legal-entity details of a business customer (1:1 with the customer; versioned through the customer row).
 */
@Getter
@Setter
@Entity
@Table(name = "business_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessProfile implements Persistable<UUID> {

    @Id
    @Setter(AccessLevel.NONE)
    @Column(name = "customer_id")
    private UUID customerId;

    @Setter(AccessLevel.NONE)
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "registered_name", nullable = false, length = 200)
    private String registeredName;

    @Column(name = "trading_name", length = 200)
    private String tradingName;

    @Column(name = "registration_number", nullable = false, length = 50)
    private String registrationNumber;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(name = "business_type", nullable = false, length = 30)
    private String businessType;

    @Column(name = "industry_sector", length = 100)
    private String industrySector;

    @Column(name = "annual_turnover_band", length = 30)
    private String annualTurnoverBand;

    @Column(name = "number_of_employees")
    private Integer numberOfEmployees;

    @Column(name = "tax_id_encrypted", length = 512)
    private String taxIdEncrypted;

    @Column(name = "tax_id_blind_index", length = 64)
    private String taxIdBlindIndex;

    @Column(name = "tax_id_masked", length = 40)
    private String taxIdMasked;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean newEntity = true;

    public BusinessProfile(UUID customerId, UUID tenantId) {
        this.customerId = customerId;
        this.tenantId = tenantId;
    }

    public void setTaxId(String encrypted, String blindIndex, String masked) {
        this.taxIdEncrypted = encrypted;
        this.taxIdBlindIndex = blindIndex;
        this.taxIdMasked = masked;
    }

    @Override
    public UUID getId() {
        return customerId;
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
