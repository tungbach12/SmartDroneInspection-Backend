ALTER TABLE inspection_reports
    DROP CONSTRAINT IF EXISTS ck_inspection_reports_status;

ALTER TABLE inspection_reports
    ADD CONSTRAINT ck_inspection_reports_status
        CHECK (status IN ('SUBMITTED', 'APPROVED', 'RETURNED', 'PUBLISHED', 'SUPERSEDED'));
