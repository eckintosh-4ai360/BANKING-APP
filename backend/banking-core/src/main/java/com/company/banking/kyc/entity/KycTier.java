package com.company.banking.kyc.entity;

import com.company.banking.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A KYC level the institution verifies customers to, with the evidence it requires. Transaction limits per tier
 * are attached in Phase 2.
 */
@Getter
@Setter
@Entity
@Table(name = "kyc_tier")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KycTier extends AuditableEntity {

    @EmbeddedId
    @Setter(AccessLevel.NONE)
    private KycTierId id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "tier_rank", nullable = false)
    private int tierRank;

    @Column(name = "requires_identification", nullable = false)
    private boolean requiresIdentification;

    @Column(name = "requires_id_document", nullable = false)
    private boolean requiresIdDocument;

    @Column(name = "requires_selfie", nullable = false)
    private boolean requiresSelfie;

    @Column(name = "requires_address", nullable = false)
    private boolean requiresAddress;

    @Column(name = "requires_proof_of_address", nullable = false)
    private boolean requiresProofOfAddress;

    @Column(name = "requires_identity_verification", nullable = false)
    private boolean requiresIdentityVerification;

    @Column(name = "requires_next_of_kin", nullable = false)
    private boolean requiresNextOfKin;

    @Column(name = "requires_signature", nullable = false)
    private boolean requiresSignature;

    @Column(name = "requires_employment_info", nullable = false)
    private boolean requiresEmploymentInfo;

    @Column(name = "active", nullable = false)
    private boolean active;

    public KycTier(KycTierId id) {
        this.id = id;
        this.active = true;
    }

    public String getCode() {
        return id.code();
    }
}
