-- V18: MF5 warranty clock, automatic ticket closure, and the before/after evidence pair.
--
-- Forward-only: V1..V17 are applied and are not edited here. This adds nullable columns to three
-- existing tables plus CHECK constraints and one partial index. No column becomes NOT NULL, no row is
-- rewritten, and nothing is dropped except constraints this file re-declares.
--
-- Authority: business-flows.md v3.3 MF5-06 / MF5-08 and database-design.md section 6.6.
--   * Warranty is a TIME-BASED FREE-REWORK OBLIGATION ONLY. The contract removed warranty retention (H)
--     and advance funding (D) in v3.3, so there is no retention amount, no hold, no release and no
--     second commission anywhere: no such column is created here, and none exists elsewhere (V15, V16).
--   * T_war is snapshotted onto the order at signing (locked_warranty_days); the clock itself starts at
--     acceptance (warranty_started_at >= accepted_at on the ticket); when the window expires with no
--     defect the SYSTEM auto-closes the ticket, completing 100% of the lifecycle.
--   * MF5-04: before/after evidence pairs are mandatory for completion.
--
-- What the database enforces here, and what it cannot:
--   DB-ENFORCED  warranty columns pair up (a window without days is refused, days must be > 0);
--                a warranty window starts no earlier than acceptance and ends after it;
--                an accepted warranty order carries its warranty_end_date;
--                a VERIFIED work log - the completion record - cannot exist without two DISTINCT
--                evidence rows pointing at it (foreign keys, so the rows really must exist);
--                BEFORE_MAINTENANCE / AFTER_MAINTENANCE evidence must hang off a maintenance work log.
--   APPLICATION RULES (not expressible as relational checks; stated here on purpose):
--                (a) the evidence a work log points at must actually carry evidence_kind
--                    BEFORE_MAINTENANCE / AFTER_MAINTENANCE respectively - comparing a column of one
--                    row against a column of another row needs a subquery, and PostgreSQL rejects
--                    subqueries inside CHECK constraints;
--                (b) that evidence must belong to the same work log (evidence.maintenance_work_log_id
--                    = the work log pointing at it) and be in upload_status 'AVAILABLE', not QUARANTINED;
--                (c) the pair must be captured at the same camera angle (MF5-04) - no column states it;
--                (d) ticket closure (MF5-06 acceptance failure, and MF5-08 expiry) runs a query for the
--                    pair before closing, per database-design.md section 7 "Ticket closure requires
--                    before/after evidence";
--                (e) warranty_end_date = acceptance instant + locked_warranty_days, and the ticket's
--                    warranty window is copied from the accepted order - both cross-table computations;
--                (f) a recurrence inside the warranty window is handled as a REWORK assignment on the
--                    same ticket (maintenance_assignments.assignment_type = 'REWORK', V8), which is free
--                    under the contract; no money concept is involved.
--
-- Pre-checks: every CHECK below sits on a populated table and is preceded by a DO block whose predicate
-- is the exact negation of the constraint, written NOT (<predicate identical to the CHECK body>), each
-- with its pre-deploy query above it. The two new evidence pointer columns are created NULL by this
-- file, so their foreign keys are provably vacuous on a V17 database - stated, not assumed - and are
-- still preceded by the exact negation so the file is safe to apply over a database where an operator
-- already populated them.


-- 1. Warranty terms on the maintenance order (MF5-03 contract snapshot, MF5-08 basis).
ALTER TABLE maintenance_orders
    ADD COLUMN IF NOT EXISTS locked_warranty_days INTEGER,
    ADD COLUMN IF NOT EXISTS warranty_end_date TIMESTAMPTZ;

COMMENT ON COLUMN maintenance_orders.locked_warranty_days IS
    'Contract-snapshotted warranty duration in days, fixed when the parties sign (example 6 or 12 months). NULL when the contract adopts no warranty. It creates a free-rework TIME obligation only: no amount is ever retained, held or released against it.';
COMMENT ON COLUMN maintenance_orders.warranty_end_date IS
    'Instant the warranty window closes, computed at acceptance as acceptance + locked_warranty_days. NULL when no warranty is adopted, and NULL until the order is accepted.';


-- 2. Pre-check for ck_maintenance_orders_warranty.
--
--    Pre-deploy query (must return zero rows):
--      SELECT id, status, locked_warranty_days, warranty_end_date, completed_at FROM maintenance_orders
--       WHERE NOT ( <this CHECK body> );
--
--    Remediation: a warranty_end_date without locked_warranty_days means the days were lost - restore
--    them from the signed contract (UPDATE maintenance_orders SET locked_warranty_days = <n> WHERE id =
--    <id>). Days <= 0 and an accepted warranty order without its end date have no automatic repair:
--    set the column to the value the contract produces, or clear locked_warranty_days to NULL if the
--    contract adopts no warranty, then rerun.
DO $$
DECLARE
    bad_warranty TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO bad_warranty
      FROM (
              SELECT id || ' -> ' || status
                     || ' (days=' || COALESCE(locked_warranty_days::text, 'null')
                     || ', end=' || COALESCE(warranty_end_date::text, 'null')
                     || ', completed=' || COALESCE(completed_at::text, 'null') || ')' AS label
                FROM maintenance_orders
               WHERE NOT (
                     (locked_warranty_days IS NULL OR locked_warranty_days > 0)
                 AND (warranty_end_date IS NULL OR locked_warranty_days IS NOT NULL)
                 AND (warranty_end_date IS NOT NULL
                      OR locked_warranty_days IS NULL
                      OR status NOT IN ('COMPLETED', 'AWAITING_PAYMENT', 'PAID'))
                 AND (completed_at IS NULL
                      OR warranty_end_date IS NULL
                      OR warranty_end_date > completed_at)
               )
               ORDER BY id
               LIMIT 20
           ) offender;

    IF bad_warranty IS NOT NULL THEN
        RAISE EXCEPTION
            'V18 precheck failed: maintenance_orders warranty terms are inconsistent - locked_warranty_days must be NULL or > 0, a warranty_end_date requires locked_warranty_days, an accepted warranty order must carry its warranty_end_date, and the window must end after acceptance (first 20): %. Remediation: restore the snapshotted warranty days and end instant from the signed contract, or clear both to NULL when the contract adopts no warranty, then rerun.',
            bad_warranty;
    END IF;
END $$;

ALTER TABLE maintenance_orders DROP CONSTRAINT IF EXISTS ck_maintenance_orders_warranty;
ALTER TABLE maintenance_orders
    ADD CONSTRAINT ck_maintenance_orders_warranty CHECK (
        (locked_warranty_days IS NULL OR locked_warranty_days > 0)
        AND (warranty_end_date IS NULL OR locked_warranty_days IS NOT NULL)
        AND (warranty_end_date IS NOT NULL
             OR locked_warranty_days IS NULL
             OR status NOT IN ('COMPLETED', 'AWAITING_PAYMENT', 'PAID'))
        AND (completed_at IS NULL
             OR warranty_end_date IS NULL
             OR warranty_end_date > completed_at)
    );


-- 3. The warranty clock and the acceptance instant on the ticket (MF5-08).
ALTER TABLE maintenance_tickets
    ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS warranty_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS warranty_ends_at TIMESTAMPTZ;

COMMENT ON COLUMN maintenance_tickets.accepted_at IS
    'MF5-06 completion acceptance: the instant both parties signed the completion acceptance minutes (or the system accepted under the contract terms).';
COMMENT ON COLUMN maintenance_tickets.warranty_started_at IS
    'MF5-08: the warranty countdown starts here, at acceptance. NULL when the accepted order adopts no warranty.';
COMMENT ON COLUMN maintenance_tickets.warranty_ends_at IS
    'MF5-08: when the warranty window closes with no defect the SYSTEM auto-closes the ticket, completing the lifecycle. NULL when no warranty is adopted.';


-- 4. Pre-check for ck_maintenance_tickets_warranty.
--
--    Pre-deploy query (must return zero rows):
--      SELECT id, status, accepted_at, warranty_started_at, warranty_ends_at FROM maintenance_tickets
--       WHERE NOT ( <this CHECK body> );
--
--    Remediation: a warranty window must start at or after acceptance and end after it starts. Set the
--    instants to the values the contract produces (acceptance first, then start = acceptance, end =
--    start + locked_warranty_days), or clear all three to NULL when no warranty applies, then rerun.
DO $$
DECLARE
    bad_clock TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO bad_clock
      FROM (
              SELECT id || ' -> ' || status
                     || ' (accepted=' || COALESCE(accepted_at::text, 'null')
                     || ', start=' || COALESCE(warranty_started_at::text, 'null')
                     || ', end=' || COALESCE(warranty_ends_at::text, 'null') || ')' AS label
                FROM maintenance_tickets
               WHERE NOT (
                     (warranty_started_at IS NULL AND warranty_ends_at IS NULL)
                  OR (warranty_started_at IS NOT NULL
                      AND warranty_ends_at IS NOT NULL
                      AND accepted_at IS NOT NULL
                      AND warranty_started_at >= accepted_at
                      AND warranty_ends_at > warranty_started_at)
               )
               ORDER BY id
               LIMIT 20
           ) offender;

    IF bad_clock IS NOT NULL THEN
        RAISE EXCEPTION
            'V18 precheck failed: maintenance_tickets warranty clock is inconsistent - a window must be entirely absent or a complete pair (accepted_at, warranty_started_at >= accepted_at, warranty_ends_at > warranty_started_at) (first 20): %. Remediation: set the acceptance instant and copy the window from the accepted order, or clear all three columns when the contract adopts no warranty, then rerun.',
            bad_clock;
    END IF;
END $$;

ALTER TABLE maintenance_tickets DROP CONSTRAINT IF EXISTS ck_maintenance_tickets_warranty;
ALTER TABLE maintenance_tickets
    ADD CONSTRAINT ck_maintenance_tickets_warranty CHECK (
        (warranty_started_at IS NULL AND warranty_ends_at IS NULL)
        OR (warranty_started_at IS NOT NULL
            AND warranty_ends_at IS NOT NULL
            AND accepted_at IS NOT NULL
            AND warranty_started_at >= accepted_at
            AND warranty_ends_at > warranty_started_at)
    );

-- MF5-08 auto-close sweep: every still-open ticket whose warranty window has a closing instant.
-- Partial and non-unique, so it cannot fail on data and needs no pre-check.
CREATE INDEX ix_maintenance_tickets_warranty_close
    ON maintenance_tickets (warranty_ends_at)
    WHERE status NOT IN ('CLOSED', 'CANCELLED')
      AND warranty_ends_at IS NOT NULL;


-- 5. Before/after evidence belongs to a maintenance work log, never to an inspection.
--
--    Pre-check (exact negation):
--      SELECT id, evidence_kind, inspection_id, maintenance_work_log_id FROM evidence
--       WHERE NOT ( <this CHECK body> );
--
--    Remediation: a BEFORE_MAINTENANCE / AFTER_MAINTENANCE row with no work log cannot be re-parented
--    automatically (its work context is unknown). Re-point it at the work log it was captured for
--    (UPDATE evidence SET maintenance_work_log_id = <id>, inspection_id = NULL WHERE id = <id>), or if
--    it was mislabelled, set evidence_kind = 'INSPECTION' or 'OTHER' to match its real parent, then
--    rerun.
DO $$
DECLARE
    misparented TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO misparented
      FROM (
              SELECT id || ' -> ' || evidence_kind
                     || ' (inspection=' || COALESCE(inspection_id::text, 'null')
                     || ', work_log=' || COALESCE(maintenance_work_log_id::text, 'null') || ')'
                     AS label
                FROM evidence
               WHERE NOT (evidence_kind NOT IN ('BEFORE_MAINTENANCE', 'AFTER_MAINTENANCE')
                           OR maintenance_work_log_id IS NOT NULL)
               ORDER BY id
               LIMIT 20
           ) offender;

    IF misparented IS NOT NULL THEN
        RAISE EXCEPTION
            'V18 precheck failed: BEFORE_MAINTENANCE / AFTER_MAINTENANCE evidence must be attached to a maintenance work log, but these rows are not (first 20): %. Remediation: re-point each row at its work log (maintenance_work_log_id, inspection_id = NULL), or correct evidence_kind to match its real parent, then rerun.',
            misparented;
    END IF;
END $$;

ALTER TABLE evidence DROP CONSTRAINT IF EXISTS ck_evidence_maintenance_pair;
ALTER TABLE evidence
    ADD CONSTRAINT ck_evidence_maintenance_pair CHECK (
        evidence_kind NOT IN ('BEFORE_MAINTENANCE', 'AFTER_MAINTENANCE')
        OR maintenance_work_log_id IS NOT NULL
    );


-- 6. The evidence pair a work log completes with. Columns first, constraints after the pre-check, so
--    the predicate below can name them and so the foreign keys are only declared once every value they
--    could hold has been proven resolvable.
ALTER TABLE maintenance_work_logs
    ADD COLUMN IF NOT EXISTS before_evidence_id UUID,
    ADD COLUMN IF NOT EXISTS after_evidence_id UUID;

COMMENT ON COLUMN maintenance_work_logs.before_evidence_id IS
    'The BEFORE_MAINTENANCE evidence this completion is contrasted with (MF5-04). Required by the database once the work log is VERIFIED; which row it must be, and that it belongs to this work log, is an application rule.';
COMMENT ON COLUMN maintenance_work_logs.after_evidence_id IS
    'The AFTER_MAINTENANCE evidence showing the repair (MF5-04), required with before_evidence_id once the work log is VERIFIED. Must be a different row.';


-- 7. Pre-check for the two pointer foreign keys (exact negation of each, OR-ed; both columns are new in
--    this file, so every branch is provably empty on a V17 database):
--
--    SELECT 'before' AS which, id::text FROM maintenance_work_logs
--     WHERE NOT (before_evidence_id IS NULL
--                OR EXISTS (SELECT 1 FROM evidence e WHERE e.id = before_evidence_id))
--    UNION ALL
--    SELECT 'after', id::text FROM maintenance_work_logs
--     WHERE NOT (after_evidence_id IS NULL
--                OR EXISTS (SELECT 1 FROM evidence e WHERE e.id = after_evidence_id));
--
--    Remediation: clear the pointer (UPDATE maintenance_work_logs SET before_evidence_id = NULL WHERE
--    id = <id>) - which in turn makes the row fail ck_work_logs_evidence_pair if it is VERIFIED, so
--    decide first whether the evidence row should exist instead.
DO $$
DECLARE
    dangling TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO dangling
      FROM (
              SELECT 'before_evidence ' || id AS label
                FROM maintenance_work_logs
               WHERE NOT (before_evidence_id IS NULL
                           OR EXISTS (SELECT 1 FROM evidence e WHERE e.id = before_evidence_id))
              UNION ALL
              SELECT 'after_evidence ' || id
                FROM maintenance_work_logs
               WHERE NOT (after_evidence_id IS NULL
                           OR EXISTS (SELECT 1 FROM evidence e WHERE e.id = after_evidence_id))
              ORDER BY 1
              LIMIT 20
           ) offender;

    IF dangling IS NOT NULL THEN
        RAISE EXCEPTION
            'V18 precheck failed: a work log points at a before/after evidence row that does not exist (first 20): %. Remediation: clear the pointer, or create the evidence row it names, then rerun.',
            dangling;
    END IF;
END $$;

ALTER TABLE maintenance_work_logs
    ADD CONSTRAINT fk_work_logs_before_evidence
        FOREIGN KEY (before_evidence_id) REFERENCES evidence (id) ON DELETE RESTRICT;
ALTER TABLE maintenance_work_logs
    ADD CONSTRAINT fk_work_logs_after_evidence
        FOREIGN KEY (after_evidence_id) REFERENCES evidence (id) ON DELETE RESTRICT;


-- 8. Pre-check for the completion gate itself. A work log already VERIFIED without its pair cannot be
--    transformed: the evidence it needs does not exist anywhere, and inventing a reference or silently
--    demoting the verification would both destroy information. This is therefore refused, not repaired.
--
--    Pre-deploy query (must return zero rows):
--      SELECT id, status, before_evidence_id, after_evidence_id FROM maintenance_work_logs
--       WHERE NOT ( <this CHECK body> );
--
--    Remediation, per listed row, choose one:
--      a) the work really is complete: attach its real pair
--           UPDATE maintenance_work_logs SET before_evidence_id = <before id>,
--                                           after_evidence_id  = <after id>
--            WHERE id = <id>;
--      b) the pair was never captured: move the record back to the state it can support
--           UPDATE maintenance_work_logs SET status = 'SUBMITTED', verified_by_user_id = NULL,
--                                           verified_at = NULL WHERE id = <id>;
--    then rerun.
DO $$
DECLARE
    unpaired TEXT;
BEGIN
    SELECT string_agg(offender.label, '; ' ORDER BY offender.label)
      INTO unpaired
      FROM (
              SELECT id || ' -> ' || status
                     || ' (before=' || COALESCE(before_evidence_id::text, 'null')
                     || ', after=' || COALESCE(after_evidence_id::text, 'null') || ')' AS label
                FROM maintenance_work_logs
               WHERE NOT (status <> 'VERIFIED'
                           OR (before_evidence_id IS NOT NULL
                               AND after_evidence_id IS NOT NULL
                               AND before_evidence_id <> after_evidence_id))
               ORDER BY id
               LIMIT 20
           ) offender;

    IF unpaired IS NOT NULL THEN
        RAISE EXCEPTION
            'V18 precheck failed: a VERIFIED work log has no before/after evidence pair, and completion under MF5-04 requires one (first 20): %. Remediation: attach the real pair to each listed work log, or move records whose pair was never captured back to status = SUBMITTED with verified_by_user_id/verified_at cleared, then rerun.',
            unpaired;
    END IF;
END $$;

ALTER TABLE maintenance_work_logs DROP CONSTRAINT IF EXISTS ck_work_logs_evidence_pair;
ALTER TABLE maintenance_work_logs
    ADD CONSTRAINT ck_work_logs_evidence_pair CHECK (
        status <> 'VERIFIED'
        OR (before_evidence_id IS NOT NULL
            AND after_evidence_id IS NOT NULL
            AND before_evidence_id <> after_evidence_id)
    );
