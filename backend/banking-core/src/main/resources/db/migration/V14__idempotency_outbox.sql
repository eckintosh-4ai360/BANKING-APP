-- =====================================================================================================
-- V14 Idempotency records and the transactional outbox.
--
-- idempotency_record: written in the same transaction as the effect it protects (decision D5), so a retried
-- request either finds the stored response or re-runs a request that never committed. Same key with a different
-- payload is refused.
-- outbox_event: events written in the business transaction and relayed afterwards (decision D6); no external call
-- ever happens inside a money transaction.
-- =====================================================================================================

CREATE TABLE core.idempotency_record
(
    id              uuid         NOT NULL,
    tenant_id       uuid         NOT NULL,
    scope           varchar(120) NOT NULL,
    idempotency_key varchar(100) NOT NULL,
    request_hash    varchar(64)  NOT NULL,
    status          varchar(15)  NOT NULL,
    response_body   jsonb,
    resource_type   varchar(40),
    resource_id     uuid,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    completed_at    timestamptz,
    expires_at      timestamptz  NOT NULL,
    CONSTRAINT pk_idempotency_record PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_record_key UNIQUE (tenant_id, scope, idempotency_key),
    CONSTRAINT fk_idempotency_record_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_idempotency_record_key CHECK (idempotency_key ~ '^[A-Za-z0-9_-]{8,100}$'),
    CONSTRAINT ck_idempotency_record_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_idempotency_record_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_idempotency_record_completed CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CONSTRAINT ck_idempotency_record_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_idempotency_record_expiry ON core.idempotency_record (tenant_id, expires_at);
SELECT core.apply_tenant_isolation('core.idempotency_record');

GRANT SELECT, INSERT, DELETE ON core.idempotency_record TO ${app_db_role};
GRANT UPDATE (status, response_body, resource_type, resource_id, completed_at)
    ON core.idempotency_record TO ${app_db_role};

CREATE TABLE core.outbox_event
(
    id              uuid         NOT NULL,
    tenant_id       uuid         NOT NULL,
    aggregate_type  varchar(40)  NOT NULL,
    aggregate_id    uuid         NOT NULL,
    event_type      varchar(60)  NOT NULL,
    payload         jsonb        NOT NULL,
    occurred_at     timestamptz  NOT NULL DEFAULT now(),
    published_at    timestamptz,
    attempts        integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    last_error      varchar(300),
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT fk_outbox_event_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_outbox_event_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_outbox_event_pending ON core.outbox_event (tenant_id, next_attempt_at)
    WHERE published_at IS NULL;
SELECT core.apply_tenant_isolation('core.outbox_event');

GRANT SELECT, INSERT ON core.outbox_event TO ${app_db_role};
GRANT UPDATE (published_at, attempts, next_attempt_at, last_error) ON core.outbox_event TO ${app_db_role};
