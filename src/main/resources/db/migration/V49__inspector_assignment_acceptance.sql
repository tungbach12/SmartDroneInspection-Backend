ALTER TABLE inspections
    ADD COLUMN IF NOT EXISTS assignment_status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
    ADD COLUMN IF NOT EXISTS assignment_accepted_at TIMESTAMPTZ;

ALTER TABLE inspections
    DROP CONSTRAINT IF EXISTS ck_inspections_assignment_status;

ALTER TABLE inspections
    ADD CONSTRAINT ck_inspections_assignment_status
        CHECK (assignment_status IN ('PENDING_ACCEPTANCE', 'ACCEPTED', 'DECLINED'));

CREATE INDEX IF NOT EXISTS ix_inspections_inspector_assignment_status
    ON inspections (inspector_id, assignment_status)
    WHERE status = 'SCHEDULED';
