-- V19: remove the dead v1 peer_reviews table (contract cut-list).
--
-- Forward-only: V1..V18 are applied and are not edited here. The only schema-changing statement is
-- the DROP of one table that the current contract does not use. Nothing else is touched: no column,
-- no constraint, no row outside this table.
--
-- Why the table is dead. database-design.md section 2 records the cut explicitly: "The legacy v1
-- peer_reviews table is removed by the V12+ migration set: peer review exists in no flow of the
-- current contract (MF3 uses Inspector author-verify plus Provider Manager completeness/release
-- instead)". The Java counterpart (PeerReview entity, PeerReviewDecision, PeerReviewRepository, the
-- assign-reviewer and review endpoints, and their DTOs) is removed in the same change set, so no
-- code can read or write this table once the migration lands.
--
-- Referential safety. V7 created peer_reviews with exactly one outbound reference (report_version_id
-- -> report_versions) and no table references peer_reviews: grep over V1..V18 finds no foreign key,
-- no view, no trigger and no generated column naming it. Dropping it therefore cannot orphan,
-- break or invalidate any other constraint, index or migration.
--
-- Pre-check: PostgreSQL executes DROP TABLE unconditionally, so a populated database would silently
-- lose review history. The DO block below runs FIRST and refuses the migration while any row
-- exists, naming the offending rows, because the decision to discard review history belongs to an
-- operator and not to a deploy. The remediation is the archival SELECT quoted above the block; only
-- after the rows are archived (copied out) and removed from the table does the DROP proceed.
--
--   SELECT id, report_version_id, reviewer_user_id, decision, assigned_at, reviewed_at, review_comment
--     FROM peer_reviews
--    ORDER BY assigned_at, id;
--
--   -- after archiving the output above:
--   DELETE FROM peer_reviews;
--
-- This database is pre-production, so the expected state is an empty table and the block is a
-- no-op. Verified against a populated PostgreSQL 17 by
-- CutListMigrationOnPopulatedDatabaseTest.v19RefusesToDropAPopulatedPeerReviewsTableAndThenDropsAnEmptyOne,
-- which seeds a peer_reviews row, asserts the migration aborts naming that row, deletes it, reruns,
-- and asserts the table is gone while every other seeded row survives untouched.

DO $$
DECLARE
    row_count  BIGINT;
    offenders  TEXT;
BEGIN
    SELECT count(*)
      INTO row_count
      FROM peer_reviews;

    IF row_count > 0 THEN
        SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
          INTO offenders
          FROM (
                  SELECT p.id || ' (report_version_id=' || p.report_version_id
                         || ', reviewer=' || p.reviewer_user_id
                         || ', decision=' || p.decision || ')' AS label
                    FROM peer_reviews p
                   ORDER BY p.assigned_at, p.id
                   LIMIT 20
               ) offender;

        RAISE EXCEPTION
            'V19 precheck failed: peer_reviews still holds % row(s) and dropping the table would destroy review history silently. Rows (first 20): %. Remediation: archive them with the SELECT quoted above this block, then DELETE FROM peer_reviews; and rerun this migration. The table is removed because peer review exists in no flow of the current contract, not because its contents are unimportant.',
            row_count,
            offenders;
    END IF;
END $$;

DROP TABLE IF EXISTS peer_reviews;
