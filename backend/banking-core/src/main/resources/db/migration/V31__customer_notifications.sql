-- =====================================================================================================
-- V31 Customer notifications: the in-app inbox, push registration of trusted devices, and the customer's choice of
-- text alerts.
--
-- Alerts are produced from the outbox after the business transaction committed, so sending never holds up or undoes
-- a posting. A notification made from an event records the event, so a redelivered event adds nothing.
-- =====================================================================================================

CREATE TABLE core.customer_notification
(
    id              uuid         NOT NULL,
    tenant_id       uuid         NOT NULL,
    customer_id     uuid         NOT NULL,
    category        varchar(15)  NOT NULL,
    title           varchar(100) NOT NULL,
    body            varchar(500) NOT NULL,
    reference_type  varchar(30),
    reference_id    uuid,
    source_key      uuid,
    created_at      timestamptz  NOT NULL,
    read_at         timestamptz,
    CONSTRAINT pk_customer_notification PRIMARY KEY (id),
    CONSTRAINT fk_customer_notification_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT ck_customer_notification_category CHECK (category IN ('TRANSACTION', 'SECURITY', 'LOAN', 'ACCOUNT',
        'GENERAL'))
);

-- One notification per customer and source (an outbox event, a reminder of an installment).
CREATE UNIQUE INDEX uq_customer_notification_source ON core.customer_notification (tenant_id, customer_id, source_key)
    WHERE source_key IS NOT NULL;
CREATE INDEX ix_customer_notification_inbox ON core.customer_notification (tenant_id, customer_id, created_at);
CREATE INDEX ix_customer_notification_unread ON core.customer_notification (tenant_id, customer_id)
    WHERE read_at IS NULL;
SELECT core.apply_tenant_isolation('core.customer_notification');
GRANT SELECT, INSERT ON core.customer_notification TO ${app_db_role};
GRANT UPDATE (read_at) ON core.customer_notification TO ${app_db_role};

ALTER TABLE core.customer_device
    ADD COLUMN push_provider   varchar(10),
    ADD COLUMN push_token      varchar(512),
    ADD COLUMN push_updated_at timestamptz,
    ADD CONSTRAINT ck_customer_device_push CHECK ((push_provider IS NULL) = (push_token IS NULL)
        AND (push_provider IS NULL OR push_provider IN ('FCM', 'APNS')));
GRANT UPDATE (push_provider, push_token, push_updated_at) ON core.customer_device TO ${app_db_role};

-- Text alerts for money in and out (security notices are always texted).
ALTER TABLE core.customer_credential ADD COLUMN sms_alerts boolean NOT NULL DEFAULT true;
