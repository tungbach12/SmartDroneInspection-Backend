-- Migration V17: Streamline ReportStatus by eliminating DRAFT and AUTHOR_VERIFIED
-- Reports are auto-compiled upon submission and directly enter SUBMITTED status.

-- 1. Remove duplicate/stale unsubmitted versions for inspections that already have a submitted or published report
DELETE FROM inspection_reports
WHERE status IN ('DRAFT', 'AUTHOR_VERIFIED')
  AND inspection_id IN (
      SELECT DISTINCT inspection_id
      FROM inspection_reports
      WHERE status IN ('SUBMITTED', 'RETURNED', 'APPROVED', 'PUBLISHED', 'SUPERSEDED')
  );

-- 2. Promote any remaining DRAFT or AUTHOR_VERIFIED versions to SUBMITTED
UPDATE inspection_reports
SET status = 'SUBMITTED',
    submitted_at = COALESCE(submitted_at, created_at)
WHERE status IN ('DRAFT', 'AUTHOR_VERIFIED');

-- 3. Synchronize inspection statuses with their latest report status
-- Inspections with SUBMITTED or APPROVED reports must be UNDER_REVIEW if they were in FIELD_COMPLETED
UPDATE inspections i
SET status = 'UNDER_REVIEW'
WHERE i.status = 'FIELD_COMPLETED'
  AND EXISTS (
      SELECT 1 FROM inspection_reports r
      WHERE r.inspection_id = i.id
        AND r.status IN ('SUBMITTED', 'APPROVED')
  );

-- Inspections with PUBLISHED reports must be COMPLETED
UPDATE inspections i
SET status = 'COMPLETED'
WHERE i.status != 'COMPLETED'
  AND EXISTS (
      SELECT 1 FROM inspection_reports r
      WHERE r.inspection_id = i.id
        AND r.status = 'PUBLISHED'
  );

-- 4. Update check constraint on inspection_reports.status
ALTER TABLE inspection_reports DROP CONSTRAINT IF EXISTS ck_inspection_reports_status;

ALTER TABLE inspection_reports ADD CONSTRAINT ck_inspection_reports_status CHECK (
    (status)::text = ANY (
        ARRAY[
            'SUBMITTED'::character varying,
            'RETURNED'::character varying,
            'APPROVED'::character varying,
            'PUBLISHED'::character varying,
            'SUPERSEDED'::character varying
        ]::text[]
    )
);
