package com.company.banking.customer.entity;

import com.company.banking.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "customer_next_of_kin")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NextOfKin extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "relationship", nullable = false, length = 50)
    private String relationship;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "active", nullable = false)
    private boolean active;

    public NextOfKin(UUID id, UUID tenantId, UUID customerId) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.active = true;
    }

    public void update(String fullName, String relationship, String phone, String email, String address,
                       boolean primary) {
        this.fullName = fullName;
        this.relationship = relationship;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.primary = primary;
    }

    public void deactivate() {
        this.active = false;
        this.primary = false;
    }
}
