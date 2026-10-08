package com.company.banking.ledger.entity;

import com.company.banking.common.persistence.AuditableEntity;
import com.company.banking.ledger.model.AccountClass;
import com.company.banking.ledger.model.NormalSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A general-ledger account. Its structure (class, normal side, header flag, parent, system code) is fixed at
 * creation, both here ({@code updatable = false}) and by the database grants, because postings depend on it.
 */
@Getter
@Entity
@Table(name = "chart_of_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChartOfAccount extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, updatable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_class", nullable = false, updatable = false, length = 10)
    private AccountClass accountClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_side", nullable = false, updatable = false, length = 6)
    private NormalSide normalSide;

    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    @Column(name = "is_header", nullable = false, updatable = false)
    private boolean header;

    @Column(name = "manual_posting_allowed", nullable = false)
    private boolean manualPostingAllowed;

    @Column(name = "system_code", updatable = false, length = 40)
    private String systemCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private GlStatus status;

    @SuppressWarnings("java:S107")
    public ChartOfAccount(UUID id, UUID tenantId, String code, String name, AccountClass accountClass,
                          NormalSide normalSide, UUID parentId, boolean header, boolean manualPostingAllowed,
                          String systemCode) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.accountClass = accountClass;
        this.normalSide = normalSide;
        this.parentId = parentId;
        this.header = header;
        this.manualPostingAllowed = !header && manualPostingAllowed;
        this.systemCode = systemCode;
        this.status = GlStatus.ACTIVE;
    }

    public void update(String name, boolean manualPostingAllowed) {
        this.name = name;
        this.manualPostingAllowed = !header && manualPostingAllowed;
    }

    public void changeStatus(GlStatus status) {
        this.status = status;
    }

    public boolean isPostable() {
        return !header && status == GlStatus.ACTIVE;
    }
}
