package com.company.banking.operations.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

/**
 * Progress of one step within a run.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "eod_step")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EodStepRecord implements Persistable<EodStepRecord.Key> {

    public enum Status { PENDING, RUNNING, DONE, FAILED }

    @Embeddable
    public record Key(@Column(name = "eod_run_id") UUID runId,
                      @Column(name = "step_code", length = 40) String stepCode) implements Serializable {
    }

    @EmbeddedId
    private Key id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "step_order", nullable = false, updatable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "result", columnDefinition = "jsonb")
    private String result;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error", length = 500)
    private String error;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newEntity = true;

    public EodStepRecord(UUID runId, UUID tenantId, String stepCode, int stepOrder) {
        this.id = new Key(runId, stepCode);
        this.tenantId = tenantId;
        this.stepOrder = stepOrder;
        this.status = Status.PENDING;
    }

    public String getStepCode() {
        return id.stepCode();
    }

    public void start(Instant at) {
        this.status = Status.RUNNING;
        this.attempts++;
        this.startedAt = at;
        this.error = null;
    }

    public void done(String resultJson, Instant at) {
        this.status = Status.DONE;
        this.result = resultJson;
        this.finishedAt = at;
    }

    public void fail(String message, Instant at) {
        this.status = Status.FAILED;
        this.error = message;
        this.finishedAt = at;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newEntity = false;
    }
}
