-- V28: inspector assignment response on asset_pair_assignments (MF2-01/MF2-02).
--
-- Forward-only: V1..V27 are applied and are not edited here. Two nullable columns are added to
-- asset_pair_assignments so an Inspector's answer to an assignment is recorded separately from the
-- pair's own lifecycle. Both stay NULL for every pair that has not been answered.
--
-- Authority: Report 3 SRS 3.4 FE-03 (MF2-01 accepts or rejects with a reason; MF2-02 records the
-- response and notifies ORG_ADMIN). MF2-01 is a business decision with an accountable actor, not a
-- status change on the pairing, so it needs its own vocabulary and its own timestamp. Folding it
-- into asset_pair_assignments.status would have reused reason for two different meanings - why the
-- administrator paired an inspector with a drone, and why that inspector declined - and would have
-- left no record of when the answer arrived.
--
-- What this migration does NOT do: it does not write the response. Inspections::InspectionAssignment
-- owns that transition, and the CHECK below only constrains the vocabulary so a typo cannot enter the
-- column. It does not grant a flight permit, and assignment_response = 'ACCEPTED' is not
-- READY_FOR_FLIGHT: MF2-07 records that separate, human decision with its own reviewer and snapshot.
--
-- Idempotency: both ALTERs use ADD COLUMN IF NOT EXISTS, so re-running V28 is safe.

ALTER TABLE asset_pair_assignments
    ADD COLUMN IF NOT EXISTS assignment_response VARCHAR(24),
    ADD COLUMN IF NOT EXISTS responded_at TIMESTAMPTZ;

COMMENT ON COLUMN asset_pair_assignments.assignment_response IS
    'Inspector answer to the assignment: ACCEPTED or REJECTED (MF2-01). NULL until the assigned inspector responds. Independent of status, which tracks the pair''s own validity window rather than the answer.';
COMMENT ON COLUMN asset_pair_assignments.responded_at IS
    'Instant the assigned inspector answered. Records when the response arrived so a missed or late answer is distinguishable from no answer at all.';

-- Pre-check for ck_asset_pair_assignments_response. The predicate below is the exact negation of
-- the CHECK body, written NOT ( <identical> ) per the V15/V16/V18 convention.
--
-- Pre-deploy query (must return zero rows):
--   SELECT id, assignment_response, responded_at FROM asset_pair_assignments
--    WHERE NOT ( (assignment_response IS NULL AND responded_at IS NULL)
--             OR (assignment_response IS NOT NULL AND responded_at IS NOT NULL) );
--
-- Remediation: a response without its instant loses the audit trail, and an instant without a
-- response has no vocabulary. Set both to NULL to return the row to un-answered, or supply the
-- missing value, then rerun.
DO $$
DECLARE
    bad_response TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO bad_response
      FROM (
              SELECT id || ' -> response=' || COALESCE(assignment_response::text, 'null')
                     || ' responded_at=' || COALESCE(responded_at::text, 'null') AS label
                FROM asset_pair_assignments
               WHERE NOT ( (assignment_response IS NULL AND responded_at IS NULL)
                        OR (assignment_response IS NOT NULL AND responded_at IS NOT NULL) )
               ORDER BY id
               LIMIT 20
           ) offender;

    IF bad_response IS NOT NULL THEN
        RAISE EXCEPTION
            'V28 precheck failed: asset_pair_assignments response columns are inconsistent - assignment_response and responded_at must both be NULL or both be set (first 20): %. Remediation: set both to NULL to return the row to un-answered, or supply the missing value, then rerun.',
            bad_response;
    END IF;
END $$;

ALTER TABLE asset_pair_assignments DROP CONSTRAINT IF EXISTS ck_asset_pair_assignments_response;
ALTER TABLE asset_pair_assignments
    ADD CONSTRAINT ck_asset_pair_assignments_response CHECK (
        (assignment_response IS NULL AND responded_at IS NULL)
        OR (assignment_response IS NOT NULL AND responded_at IS NOT NULL)
    );

ALTER TABLE asset_pair_assignments DROP CONSTRAINT IF EXISTS ck_asset_pair_assignments_response_vocabulary;
ALTER TABLE asset_pair_assignments
    ADD CONSTRAINT ck_asset_pair_assignments_response_vocabulary CHECK (
        assignment_response IS NULL
        OR assignment_response IN ('ACCEPTED', 'REJECTED')
    );