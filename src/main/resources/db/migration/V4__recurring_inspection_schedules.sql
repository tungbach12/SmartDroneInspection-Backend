-- V3: Recurring Inspection Schedules and Occurrences

CREATE TABLE recurring_inspection_schedules (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    inspector_id uuid NOT NULL,
    drone_id uuid NOT NULL,
    anchor_date timestamp without time zone NOT NULL,
    timezone character varying(64) NOT NULL,
    recurrence_unit character varying(16) NOT NULL,
    recurrence_interval integer NOT NULL,
    duration_seconds bigint NOT NULL,
    reminder_days integer DEFAULT 3 NOT NULL,
    ends_at timestamp with time zone,
    next_occurrence bigint DEFAULT 0 NOT NULL,
    status character varying(32) DEFAULT 'ACTIVE' NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_recurring_inspection_schedules PRIMARY KEY (id),
    CONSTRAINT ck_recurring_schedules_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED')),
    CONSTRAINT ck_recurring_schedules_unit CHECK (recurrence_unit IN ('WEEK', 'MONTH', 'YEAR')),
    CONSTRAINT ck_recurring_schedules_interval CHECK (recurrence_interval >= 1 AND recurrence_interval <= 120),
    CONSTRAINT ck_recurring_schedules_duration CHECK (duration_seconds >= 1 AND duration_seconds <= 604800),
    CONSTRAINT ck_recurring_schedules_reminder CHECK (reminder_days >= 0 AND reminder_days <= 30),
    CONSTRAINT fk_recurring_schedules_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_recurring_schedules_asset FOREIGN KEY (asset_id, organization_id) REFERENCES assets(id, organization_id) ON DELETE RESTRICT,
    CONSTRAINT fk_recurring_schedules_creator FOREIGN KEY (created_by_user_id, organization_id) REFERENCES users(id, organization_id) ON DELETE RESTRICT,
    CONSTRAINT fk_recurring_schedules_inspector FOREIGN KEY (inspector_id, organization_id) REFERENCES users(id, organization_id) ON DELETE RESTRICT,
    CONSTRAINT fk_recurring_schedules_drone FOREIGN KEY (drone_id, organization_id) REFERENCES drones(id, organization_id) ON DELETE RESTRICT
);

CREATE INDEX ix_recurring_schedules_org_status ON recurring_inspection_schedules USING btree (organization_id, status);
CREATE INDEX ix_recurring_schedules_asset ON recurring_inspection_schedules USING btree (organization_id, asset_id);

CREATE TABLE schedule_occurrences (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    schedule_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    occurrence_index bigint NOT NULL,
    occurrence_key character varying(64) NOT NULL,
    planned_start_at timestamp with time zone NOT NULL,
    planned_end_at timestamp with time zone NOT NULL,
    status character varying(32) NOT NULL,
    inspection_id uuid,
    blocked_reason character varying(1000),
    retry_count integer DEFAULT 0 NOT NULL,
    reminder_sent_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_schedule_occurrences PRIMARY KEY (id),
    CONSTRAINT ck_schedule_occurrences_status CHECK (status IN ('GENERATED', 'BLOCKED', 'SKIPPED')),
    CONSTRAINT uq_schedule_occurrences_schedule_index UNIQUE (schedule_id, occurrence_index),
    CONSTRAINT uq_schedule_occurrences_schedule_key UNIQUE (schedule_id, occurrence_key),
    CONSTRAINT fk_schedule_occurrences_schedule FOREIGN KEY (schedule_id) REFERENCES recurring_inspection_schedules(id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_occurrences_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_schedule_occurrences_inspection FOREIGN KEY (inspection_id) REFERENCES inspections(id) ON DELETE SET NULL
);

CREATE INDEX ix_schedule_occurrences_org_status ON schedule_occurrences USING btree (organization_id, status);
CREATE INDEX ix_schedule_occurrences_schedule ON schedule_occurrences USING btree (schedule_id);
CREATE INDEX ix_schedule_occurrences_due ON schedule_occurrences USING btree (status, planned_start_at);

ALTER TABLE inspections ADD COLUMN recurring_schedule_id uuid;
ALTER TABLE inspections ADD CONSTRAINT fk_inspections_recurring_schedule FOREIGN KEY (recurring_schedule_id) REFERENCES recurring_inspection_schedules(id) ON DELETE SET NULL;
