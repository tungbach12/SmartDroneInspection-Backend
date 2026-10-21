ALTER TABLE field_sessions
    ADD COLUMN outcome_notes VARCHAR(4000);

ALTER TABLE inspection_preparations
    DROP CONSTRAINT ck_inspection_preparations_status,
    ADD CONSTRAINT ck_inspection_preparations_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'READY', 'SUPERSEDED'));
