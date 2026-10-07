package com.company.banking.customer.entity;

import com.company.banking.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "customer_address")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerAddress extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "address_type", nullable = false, length = 20)
    private String addressType;

    @Column(name = "line1", nullable = false, length = 200)
    private String line1;

    @Column(name = "line2", length = 200)
    private String line2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "digital_address", length = 20)
    private String digitalAddress;

    @Column(name = "landmark", length = 200)
    private String landmark;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    public CustomerAddress(UUID id, UUID tenantId, UUID customerId) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.active = true;
    }

    public void update(String addressType, String line1, String line2, String city, String district, String region,
                       String countryCode, String digitalAddress, String landmark) {
        this.addressType = addressType;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.district = district;
        this.region = region;
        this.countryCode = countryCode;
        this.digitalAddress = digitalAddress;
        this.landmark = landmark;
        this.verifiedAt = null;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public void deactivate() {
        this.active = false;
        this.primary = false;
    }
}
