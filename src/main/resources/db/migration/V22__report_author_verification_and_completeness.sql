-- V22: report author verification + completeness provenance on report_versions (MF3-07/MF3-08).
--
-- Forward-only: V1..V21 are applied and are not edited here. Six nullable columns are added to
-- report_versions. No column becomes NOT NULL, no existing CHECK is dropped or rewritten, no row is
-- deleted, and no status vocabulary changes (AUTHOR_VERIFIED status rename is deferred - see the
-- MF3 follow-up). Backfill sets author_verified_* from created_by_user_id / technically_approved_at
-- where technically_approved_at IS NOT NULL.
--
-- Authority: database-design.md 659-661 + GAP-D-04. The author sign-off that submitForReview
-- performs (MF3-07) and the Provider Manager completeness decision that release performs (MF3-08)
-- are events worth recording with their actor and a snapshot of what the author was shown.
--
-- Idempotency: every ALTER uses ADD COLUMN IF NOT EXISTS, and the backfill UPDATE is a no-op on
-- rows already stamped, so re-running V22 is safe.

ALTER TABLE report_versions
    ADD COLUMN IF NOT EXISTS author_verified_by_user_id UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS author_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS author_verification_snapshot JSONB,
    ADD COLUMN IF NOT EXISTS completeness_checked_by_user_id UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS completeness_checked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completeness_return_reason VARCHAR(2000);

COMMENT ON COLUMN report_versions.author_verified_by_user_id IS
    'Inspector who signed the author verification (MF3-07, BR-23). Set by submitForReview.';
COMMENT ON COLUMN report_versions.author_verified_at IS
    'Instant of author sign-off; equals technically_approved_at at that step.';
COMMENT ON COLUMN report_versions.author_verification_snapshot IS
    'JSONB snapshot of the report content the author signed off on (audit trail).';
COMMENT ON COLUMN report_versions.completeness_checked_by_user_id IS
    'Provider Manager who ran the completeness gate at release (MF3-08, BR-24).';
COMMENT ON COLUMN report_versions.completeness_checked_at IS
    'Instant the completeness gate ran at release.';
COMMENT ON COLUMN report_versions.completeness_return_reason IS
    'Reason supplied when completeness fails and the version is returned for rework.';

-- Backfill provenance from the existing technical-approval instant for rows that already
-- completed MF3-07 before V22 existed. created_by_user_id is the authoring inspector.
UPDATE report_versions
   SET author_verified_by_user_id = created_by_user_id,
       author_verified_at = technically_approved_at
 WHERE technically_approved_at IS NOT NULL
   AND author_verified_by_user_id IS NULL;

-- V15-style DO precheck: refuse to apply if any backfilled row lost its provenance pairing.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM report_versions
         WHERE technically_approved_at IS NOT NULL
           AND (author_verified_by_user_id IS NULL OR author_verified_at IS NULL)
    ) THEN
        RAISE EXCEPTION 'V22 backfill failed: rows with technically_approved_at but no author_verified_* provenance remain';
    END IF;
END $$;
