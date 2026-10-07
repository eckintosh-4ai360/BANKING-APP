package com.company.banking.kyc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evidence recorded on a KYC case (electronic or manual). Append-only.
 */
@Getter
@Entity
@Immutable
@Table(name = "kyc_check")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KycCheck {

    public static final String IDENTITY_VERIFICATION = "IDENTITY_VERIFICATION";
    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "kyc_case_id", nullable = false)
    private UUID kycCaseId;

    @Column(name = "check_type", nullable = false, length = 30)
    private String checkType;

    @Column(name = "method", nullable = false, length = 20)
    private String method;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    public KycCheck(UUID id, UUID tenantId, UUID kycCaseId, String checkType, String method, String provider,
                    String result, BigDecimal score, String providerReference, String note, UUID performedBy,
                    Instant performedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.kycCaseId = kycCaseId;
        this.checkType = checkType;
        this.method = method;
        this.provider = provider;
        this.result = result;
        this.score = score;
        this.providerReference = providerReference;
        this.note = note;
        this.performedBy = performedBy;
        this.performedAt = performedAt;
    }

    public boolean passed() {
        return PASS.equals(result);
    }
}
