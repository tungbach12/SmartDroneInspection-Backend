-- =============================================================================
-- V26: Drop redundant findings_snapshot_hash and report rendered_object_key /
--      rendered_checksum_sha256 columns.
-- - Findings confirmation is tracked by inspections.findings_confirmed_at and
--   automatically reset whenever evidence or findings are modified.
-- - Published report PDFs are stored at deterministic object keys derived from
--   the report version UUID ("reports/<id>.pdf" and "maintenance-reports/<id>.pdf").
-- =============================================================================

ALTER TABLE inspections
    DROP COLUMN IF EXISTS findings_snapshot_hash;

ALTER TABLE inspection_reports
    DROP COLUMN IF EXISTS rendered_object_key,
    DROP COLUMN IF EXISTS rendered_checksum_sha256;

ALTER TABLE maintenance_reports
    DROP COLUMN IF EXISTS rendered_object_key,
    DROP COLUMN IF EXISTS rendered_checksum_sha256;
