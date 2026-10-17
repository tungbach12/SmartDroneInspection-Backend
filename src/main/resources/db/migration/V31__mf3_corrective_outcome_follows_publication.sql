-- MF3-12: a corrective-action outcome belongs to the published report version that precedes it.
-- The V30 cutover backfilled the pre-cutover reviewer disposition onto every decided finding,
-- including findings on inspections that had not been published yet, so those outcomes predate the
-- publication they would now be attributed to. Clear the ones whose inspection has not completed
-- the MF3-13 handoff, so the reviewer records a real post-publication outcome; an inspection that
-- was already handed off keeps its recorded history.
UPDATE verified_findings vf
   SET corrective_action_required = TRUE,
       corrective_action_priority = NULL,
       corrective_action_due_at = NULL,
       corrective_action_reviewed_by_user_id = NULL,
       corrective_action_reviewed_at = NULL,
       corrective_action_rationale = NULL
 WHERE vf.corrective_action_reviewed_by_user_id IS NOT NULL
   AND vf.decision IN ('CONFIRMED', 'MODIFIED')
   AND EXISTS (
       SELECT 1
         FROM inspections i
        WHERE i.id = vf.inspection_id
          AND i.corrective_handoff_at IS NULL
          AND COALESCE(
                  (
                      SELECT max(v.published_at)
                        FROM inspection_reports ir
                        JOIN inspection_report_versions v ON v.inspection_report_id = ir.id
                       WHERE ir.inspection_id = i.id
                         AND v.status = 'PUBLISHED'
                  ),
                  'infinity'::timestamptz
              ) > vf.corrective_action_reviewed_at
   );
