package com.company.banking.operations.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * One end-of-day run: closing {@code businessDate}, after which branches work on {@code nextBusinessDate}.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "eod_run")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EodRun {

    public enum Status { RUNNING, FAILED, COMPLETED }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "next_business_date", nullable = false, updatable = false)
    private LocalDate nextBusinessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "started_by", updatable = false)
    private UUID startedBy;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failed_step", length = 40)
    private String failedStep;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public EodRun(UUID id, UUID tenantId, LocalDate businessDate, LocalDate nextBusinessDate, Instant startedAt,
                  UUID startedBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.businessDate = businessDate;
        this.nextBusinessDate = nextBusinessDate;
        this.status = Status.RUNNING;
        this.startedAt = startedAt;
        this.startedBy = startedBy;
        this.attempts = 1;
    }

    public void fail(String step, String message) {
        this.status = Status.FAILED;
        this.failedStep = step;
        this.failureMessage = message;
    }

    public void resume() {
        this.status = Status.RUNNING;
        this.failedStep = null;
        this.failureMessage = null;
        this.attempts++;
    }

    public void complete(Instant at) {
        this.status = Status.COMPLETED;
        this.finishedAt = at;
    }
}
