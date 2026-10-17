-- MF3-12: retain the qualified reviewer’s corrective-action outcome separately from the Inspector proposal.
ALTER TABLE verified_findings
    ADD COLUMN corrective_action_required BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN corrective_action_priority VARCHAR(24),
    ADD COLUMN corrective_action_due_at TIMESTAMPTZ,
    ADD COLUMN corrective_action_reviewed_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN corrective_action_reviewed_at TIMESTAMPTZ,
    ADD COLUMN corrective_action_rationale VARCHAR(4000);

ALTER TABLE inspections
    ADD COLUMN corrective_handoff_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN corrective_handoff_at TIMESTAMPTZ,
    ADD COLUMN no_repair_rationale VARCHAR(2000);

-- Preserve the current implementation's independent reviewer outcome during cutover so existing
-- confirmed/modified findings do not become falsely undecided under the new explicit fields.
UPDATE verified_findings
   SET corrective_action_required = repair_required,
       corrective_action_reviewed_by_user_id = decided_by_user_id,
       corrective_action_reviewed_at = decided_at,
       corrective_action_rationale = COALESCE(rationale, 'Migrated from the existing reviewer disposition')
 WHERE decided_by_user_id IS NOT NULL
   AND decision IN ('CONFIRMED', 'MODIFIED');

-- The new boundary makes report publication separate from MF4 handoff. Existing published reports
-- already used repair_required to close or hand off their inspection; preserve that completed outcome.
UPDATE inspections i
   SET corrective_handoff_by_user_id = r.reviewer_user_id,
       corrective_handoff_at = r.published_at,
       no_repair_rationale = CASE
           WHEN i.status = 'COMPLETED' THEN 'Migrated from the existing published no-repair outcome'
           ELSE NULL
       END
  FROM inspection_reports ir
  JOIN inspection_report_versions r ON r.inspection_report_id = ir.id
 WHERE ir.inspection_id = i.id
   AND r.status = 'PUBLISHED'
   AND i.status IN ('COMPLETED', 'REPAIR_PENDING');
