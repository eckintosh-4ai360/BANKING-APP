package com.company.banking.tenant.entity;

import com.company.banking.common.error.BankingException;
import com.company.banking.tenant.exception.TenantErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * A feature's licence (granted by the platform) and enablement (chosen by the institution) for one tenant.
 */
@Getter
@Entity
@Table(name = "tenant_feature")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantFeature {

    @EmbeddedId
    private TenantFeatureId id;

    @Column(name = "licensed", nullable = false)
    private boolean licensed;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public TenantFeature(UUID tenantId, String featureCode, boolean licensed) {
        this.id = new TenantFeatureId(tenantId, featureCode);
        this.licensed = licensed;
        this.enabled = licensed;
    }

    public String getFeatureCode() {
        return id.featureCode();
    }

    /**
     * Platform licensing. Revoking a licence also disables the feature.
     */
    public void setLicensed(boolean licensed) {
        this.licensed = licensed;
        if (!licensed) {
            this.enabled = false;
        }
    }

    /**
     * Institution choice, only within the licence.
     */
    public void setEnabled(boolean enabled) {
        if (enabled && !licensed) {
            throw new BankingException(TenantErrorCode.FEATURE_NOT_LICENSED);
        }
        this.enabled = enabled;
    }
}
