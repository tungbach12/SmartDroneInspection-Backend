-- V27: Remove redundant intermediate APPROVED status from inspection_reports.
-- Managers directly review, approve, and publish the report in a single action (SUBMITTED -> PUBLISHED).

UPDATE inspection_reports
SET status = 'PUBLISHED'
WHERE status = 'APPROVED';

ALTER TABLE inspection_reports DROP CONSTRAINT IF EXISTS ck_inspection_reports_status;

ALTER TABLE inspection_reports ADD CONSTRAINT ck_inspection_reports_status CHECK (
    (status)::text = ANY (
        ARRAY[
            'SUBMITTED'::character varying,
            'RETURNED'::character varying,
            'PUBLISHED'::character varying,
            'SUPERSEDED'::character varying
        ]::text[]
    )
);
