-- MF3 findings confirmation is an auditable gate independent of report drafting.
ALTER TABLE inspections
    ADD COLUMN findings_confirmed_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN findings_confirmed_at TIMESTAMPTZ,
    ADD COLUMN findings_snapshot_hash VARCHAR(64);

ALTER TABLE verified_findings
    ALTER COLUMN location_description DROP NOT NULL;

ALTER TABLE inspection_report_versions
    ADD COLUMN findings_snapshot_hash VARCHAR(64);
