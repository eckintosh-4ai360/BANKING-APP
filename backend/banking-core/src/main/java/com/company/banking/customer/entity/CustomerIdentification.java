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
import java.time.LocalDate;
import java.util.UUID;

/**
 * An identity document of a customer. The number exists only encrypted, as a blind index (duplicate detection)
 * and as a masked display value.
 */
@Getter
@Entity
@Table(name = "customer_identification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerIdentification extends AuditableEntity {

    public static final String UNVERIFIED = "UNVERIFIED";
    public static final String VERIFIED = "VERIFIED";
    public static final String FAILED = "FAILED";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "id_type_code", nullable = false, updatable = false, length = 30)
    private String idTypeCode;

    @Column(name = "id_number_encrypted", nullable = false, updatable = false, length = 512)
    private String idNumberEncrypted;

    @Column(name = "id_number_blind_index", nullable = false, updatable = false, length = 64)
    private String idNumberBlindIndex;

    @Column(name = "id_number_masked", nullable = false, updatable = false, length = 40)
    private String idNumberMasked;

    @Column(name = "issuing_country", length = 2)
    private String issuingCountry;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "verification_status", nullable = false, length = 20)
    private String verificationStatus;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verification_reference", length = 100)
    private String verificationReference;

    public CustomerIdentification(UUID id, UUID tenantId, UUID customerId, String idTypeCode, String encrypted,
                                  String blindIndex, String masked, String issuingCountry, LocalDate issueDate,
                                  LocalDate expiryDate) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.idTypeCode = idTypeCode;
        this.idNumberEncrypted = encrypted;
        this.idNumberBlindIndex = blindIndex;
        this.idNumberMasked = masked;
        this.issuingCountry = issuingCountry;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.active = true;
        this.verificationStatus = UNVERIFIED;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public void deactivate() {
        this.active = false;
        this.primary = false;
    }

    public void recordVerification(boolean passed, String reference, Instant now) {
        this.verificationStatus = passed ? VERIFIED : FAILED;
        this.verificationReference = reference;
        this.verifiedAt = passed ? now : null;
    }

    public boolean isExpired(LocalDate today) {
        return expiryDate != null && !expiryDate.isAfter(today);
    }
}
