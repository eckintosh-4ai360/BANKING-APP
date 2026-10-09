package com.company.banking.loan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * A delinquency band of the institution: from {@code minDays} days past due (up to the next band) a loan is held at
 * this provision rate, and with {@code suspendAccrual} its interest stops being recognised as income. The set is
 * replaced as a whole, never edited row by row.
 */
@Getter
@Entity
@Table(name = "loan_delinquency_band")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanDelinquencyBand implements Persistable<LoanDelinquencyBand.Key> {

    @Embeddable
    public record Key(@Column(name = "tenant_id") UUID tenantId, @Column(name = "code") String code)
            implements Serializable {
    }

    @EmbeddedId
    private Key id;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "min_days", nullable = false)
    private int minDays;

    @Column(name = "provision_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal provisionRate;

    @Column(name = "suspend_accrual", nullable = false)
    private boolean suspendAccrual;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public LoanDelinquencyBand(UUID tenantId, String code, String name, int minDays, BigDecimal provisionRate,
                               boolean suspendAccrual, Instant updatedAt) {
        this.id = new Key(tenantId, code);
        this.name = name;
        this.minDays = minDays;
        this.provisionRate = provisionRate;
        this.suspendAccrual = suspendAccrual;
        this.updatedAt = updatedAt;
    }

    public String getCode() {
        return id.code();
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
