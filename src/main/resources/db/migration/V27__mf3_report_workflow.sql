-- V27: align the inspection report aggregate with the Report 3 MF3 target workflow.
--
-- V26 retired the marketplace tables and dropped the legacy report_versions/peer_reviews pair, but
-- inspection_reports.status kept the retired five-role vocabulary (AWAITING_PEER_REVIEW,
-- TECHNICALLY_APPROVED, RELEASED, REVISION_REQUESTED, ACCEPTED). Report 3 replaces it with an
-- author-verified, reviewer-approved, published lifecycle, and peer_reviews is no longer part of the
-- 41-table target inventory.
--
-- No row is reinterpreted: existing rows cannot be mapped onto the new lifecycle, so a legacy
-- status is translated to DRAFT, which keeps the report revisable rather than falsely approved.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = current_schema() AND table_name = 'peer_reviews') THEN
        EXECUTE 'DROP TABLE peer_reviews';
    END IF;
END $$;

ALTER TABLE inspection_reports DROP CONSTRAINT IF EXISTS ck_inspection_reports_status;

UPDATE inspection_reports
   SET status = 'DRAFT'
 WHERE status NOT IN ('DRAFT', 'AUTHOR_VERIFIED', 'SUBMITTED', 'RETURNED', 'APPROVED',
                      'PUBLISHED', 'SUPERSEDED');

ALTER TABLE inspection_reports
    ADD CONSTRAINT ck_inspection_reports_status CHECK (status IN (
        'DRAFT', 'AUTHOR_VERIFIED', 'SUBMITTED', 'RETURNED',
        'APPROVED', 'PUBLISHED', 'SUPERSEDED'
    ));

-- The MF3 aggregate carries the reviewer and approval record on the version; the parent row only
-- needs to keep its lifecycle in step with the current version.
COMMENT ON TABLE inspection_reports IS
    'MF3 report aggregate. The human gates live on inspection_report_versions; see Report 3 sections 3.7.1 and 3.7.3.';