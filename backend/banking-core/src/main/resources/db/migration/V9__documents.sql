-- =====================================================================================================
-- V9 Documents: encrypted binary objects in object storage plus their metadata, and customer KYC documents.
-- =====================================================================================================

CREATE TABLE core.stored_document
(
    id                     uuid         NOT NULL,
    tenant_id              uuid         NOT NULL,
    owner_type             varchar(30)  NOT NULL,
    owner_id               uuid         NOT NULL,
    file_name              varchar(255) NOT NULL,
    content_type           varchar(100) NOT NULL,
    size_bytes             bigint       NOT NULL,
    sha256                 varchar(64)  NOT NULL,
    storage_key            varchar(300) NOT NULL,
    encryption_key_version smallint     NOT NULL,
    scan_status            varchar(20)  NOT NULL,
    uploaded_by            uuid,
    uploaded_at            timestamptz  NOT NULL,
    version                bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_stored_document PRIMARY KEY (id),
    CONSTRAINT uq_stored_document_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_stored_document_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_stored_document_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_stored_document_size CHECK (size_bytes > 0),
    CONSTRAINT ck_stored_document_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_stored_document_scan CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'SKIPPED'))
);

CREATE INDEX ix_stored_document_owner ON core.stored_document (tenant_id, owner_type, owner_id);

SELECT core.apply_tenant_isolation('core.stored_document');
-- Metadata is write-once except for the malware scan result.
GRANT SELECT, INSERT, UPDATE (scan_status, version) ON core.stored_document TO ${app_db_role};


CREATE TABLE core.customer_document
(
    id                 uuid        NOT NULL,
    tenant_id          uuid        NOT NULL,
    customer_id        uuid        NOT NULL,
    stored_document_id uuid        NOT NULL,
    document_type      varchar(40) NOT NULL,
    review_status      varchar(20) NOT NULL,
    review_note        varchar(255),
    reviewed_by        uuid,
    reviewed_at        timestamptz,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    created_by         uuid,
    updated_by         uuid,
    version            bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_document PRIMARY KEY (id),
    CONSTRAINT fk_customer_document_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT fk_customer_document_stored FOREIGN KEY (tenant_id, stored_document_id)
        REFERENCES core.stored_document (tenant_id, id),
    CONSTRAINT ck_customer_document_type CHECK (document_type IN
                                                ('ID_FRONT', 'ID_BACK', 'SELFIE', 'PROOF_OF_ADDRESS', 'SIGNATURE',
                                                 'BUSINESS_REGISTRATION', 'TAX_CERTIFICATE', 'EMPLOYMENT_LETTER',
                                                 'OTHER')),
    CONSTRAINT ck_customer_document_review CHECK (review_status IN ('PENDING_REVIEW', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_customer_document_reviewed CHECK ((review_status = 'PENDING_REVIEW') = (reviewed_by IS NULL))
);

CREATE INDEX ix_customer_document_customer ON core.customer_document (tenant_id, customer_id);

SELECT core.apply_tenant_isolation('core.customer_document');
GRANT SELECT, INSERT, UPDATE ON core.customer_document TO ${app_db_role};
