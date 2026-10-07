package com.company.banking.customer.entity;

import com.company.banking.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A person connected to a business customer: director, shareholder, beneficial owner, signatory.
 */
@Getter
@Setter
@Entity
@Table(name = "customer_related_party")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RelatedParty extends AuditableEntity {

    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Setter(AccessLevel.NONE)
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Setter(AccessLevel.NONE)
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "related_customer_id")
    private UUID relatedCustomerId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "party_role", nullable = false, length = 30)
    private String partyRole;

    @Column(name = "ownership_percent", precision = 5, scale = 2)
    private BigDecimal ownershipPercent;

    @Column(name = "nationality", length = 2)
    private String nationality;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "id_type_code", length = 30)
    private String idTypeCode;

    @Column(name = "id_number_encrypted", length = 512)
    private String idNumberEncrypted;

    @Column(name = "id_number_blind_index", length = 64)
    private String idNumberBlindIndex;

    @Column(name = "id_number_masked", length = 40)
    private String idNumberMasked;

    @Column(name = "politically_exposed", nullable = false)
    private boolean politicallyExposed;

    @Setter(AccessLevel.NONE)
    @Column(name = "active", nullable = false)
    private boolean active;

    public RelatedParty(UUID id, UUID tenantId, UUID customerId) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.active = true;
    }

    public void setIdentification(String idTypeCode, String encrypted, String blindIndex, String masked) {
        this.idTypeCode = idTypeCode;
        this.idNumberEncrypted = encrypted;
        this.idNumberBlindIndex = blindIndex;
        this.idNumberMasked = masked;
    }

    public void deactivate() {
        this.active = false;
    }
}
