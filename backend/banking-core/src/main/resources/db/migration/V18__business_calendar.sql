-- =====================================================================================================
-- V18 Business date and business calendar.
--
-- The business date is no longer the calendar date: it is stored per institution and only end-of-day processing
-- moves it forward, to the next working day of the institution's calendar (working week minus holidays). Every
-- posting carries it.
-- =====================================================================================================

INSERT INTO core.permission (code, module, description, scope, sensitive)
VALUES ('operations.view', 'operations', 'View the business date, holidays and end-of-day runs', 'TENANT', false),
       ('operations.manage', 'operations', 'Change the business calendar and run end-of-day', 'TENANT', true);

CREATE TABLE core.business_day
(
    tenant_id              uuid        NOT NULL,
    business_date          date        NOT NULL,
    previous_business_date date,
    updated_at             timestamptz NOT NULL,
    updated_by             uuid,
    version                bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_business_day PRIMARY KEY (tenant_id),
    CONSTRAINT fk_business_day_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_business_day_order CHECK (previous_business_date IS NULL OR previous_business_date < business_date)
);

SELECT core.apply_tenant_isolation('core.business_day');

-- The business date only moves forward; postings already made on a date can never be "reopened" by moving back.
CREATE FUNCTION core.business_day_forward_only() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF NEW.business_date <= OLD.business_date THEN
        RAISE EXCEPTION 'The business date can only move forward' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_business_day_forward_only
    BEFORE UPDATE OF business_date ON core.business_day
    FOR EACH ROW EXECUTE FUNCTION core.business_day_forward_only();
CREATE TRIGGER trg_business_day_no_delete
    BEFORE DELETE ON core.business_day
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();

GRANT SELECT, INSERT ON core.business_day TO ${app_db_role};
GRANT UPDATE (business_date, previous_business_date, updated_at, updated_by, version)
    ON core.business_day TO ${app_db_role};

-- ---------------------------------------------------------------------------------------------- calendar

CREATE TABLE core.business_calendar
(
    tenant_id    uuid        NOT NULL,
    working_week varchar(7)  NOT NULL,
    updated_at   timestamptz NOT NULL,
    updated_by   uuid,
    version      bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_business_calendar PRIMARY KEY (tenant_id),
    CONSTRAINT fk_business_calendar_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    -- Monday to Sunday, 1 = working day; at least one working day.
    CONSTRAINT ck_business_calendar_week CHECK (working_week ~ '^[01]{7}$' AND working_week <> '0000000')
);

SELECT core.apply_tenant_isolation('core.business_calendar');
GRANT SELECT, INSERT ON core.business_calendar TO ${app_db_role};
GRANT UPDATE (working_week, updated_at, updated_by, version) ON core.business_calendar TO ${app_db_role};

CREATE TABLE core.holiday
(
    tenant_id    uuid         NOT NULL,
    holiday_date date         NOT NULL,
    name         varchar(100) NOT NULL,
    created_at   timestamptz  NOT NULL,
    created_by   uuid,
    CONSTRAINT pk_holiday PRIMARY KEY (tenant_id, holiday_date),
    CONSTRAINT fk_holiday_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id)
);

SELECT core.apply_tenant_isolation('core.holiday');

-- Only future days can be declared or removed as holidays: the past of the calendar is what EOD already used.
CREATE FUNCTION core.holiday_future_only() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_tenant  uuid := CASE WHEN TG_OP = 'DELETE' THEN OLD.tenant_id ELSE NEW.tenant_id END;
    v_date    date := CASE WHEN TG_OP = 'DELETE' THEN OLD.holiday_date ELSE NEW.holiday_date END;
    v_current date;
BEGIN
    SELECT business_date INTO v_current FROM core.business_day WHERE tenant_id = v_tenant;
    IF v_current IS NOT NULL AND v_date <= v_current THEN
        RAISE EXCEPTION 'Holidays can only be changed for days after the current business date'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE TRIGGER trg_holiday_future_only
    BEFORE INSERT OR UPDATE OR DELETE ON core.holiday
    FOR EACH ROW EXECUTE FUNCTION core.holiday_future_only();

GRANT SELECT, INSERT, DELETE ON core.holiday TO ${app_db_role};
