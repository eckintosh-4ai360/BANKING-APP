package com.company.banking.tenant.entity;

import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A financial institution on the platform. This is the platform's registry record; the institution's own
 * branding and contact data live in {@link InstitutionProfile}.
 */
@Getter
@Entity
@Table(name = "tenant")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false, length = 32)
    private String code;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "institution_type", nullable = false, length = 30)
    private InstitutionType institutionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantStatus status;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "locale", nullable = false, length = 20)
    private String locale;

    @Column(name = "licence_number", length = 64)
    private String licenceNumber;

    public Tenant(UUID id, String code, String legalName, String displayName, InstitutionType institutionType,
                  String countryCode, String baseCurrency, String timezone, String locale, String licenceNumber) {
        this.id = id;
        this.code = code;
        this.legalName = legalName;
        this.displayName = displayName;
        this.institutionType = institutionType;
        this.status = TenantStatus.ONBOARDING;
        this.countryCode = countryCode;
        this.baseCurrency = baseCurrency;
        this.timezone = timezone;
        this.locale = locale;
        this.licenceNumber = licenceNumber;
    }

    public void changeStatus(TenantStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BankingException(CommonErrorCode.INVALID_STATE_TRANSITION,
                    "Institution cannot move from " + status + " to " + target + ".");
        }
        this.status = target;
    }

    public void rename(String displayName) {
        this.displayName = displayName;
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }
}
