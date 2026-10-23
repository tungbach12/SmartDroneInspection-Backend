-- MF3 field audit: preserve source information while aligning persisted fields with the target workflow.
-- This is a forward-only migration. Unknown or ambiguous historical data fails before fields are removed.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM findings f
        WHERE LENGTH(CONCAT_WS(
            E'\n\n',
            NULLIF(BTRIM(f.technical_notes), ''),
            CASE
                WHEN NULLIF(BTRIM(COALESCE(f.description, '')), '') IS NOT NULL
                     AND BTRIM(f.description) IS DISTINCT FROM BTRIM(COALESCE(f.technical_notes, ''))
                THEN 'Description: ' || BTRIM(f.description)
            END,
            CASE
                WHEN NULLIF(BTRIM(COALESCE(f.observed_condition, '')), '') IS NOT NULL
                     AND BTRIM(f.observed_condition) IS DISTINCT FROM BTRIM(COALESCE(f.technical_notes, ''))
                     AND BTRIM(f.observed_condition) IS DISTINCT FROM BTRIM(COALESCE(f.description, ''))
                THEN 'Observed condition: ' || BTRIM(f.observed_condition)
            END
        )) > 4000
    ) THEN
        RAISE EXCEPTION 'V39 cannot merge finding notes: combined technical_notes exceeds 4000 characters';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM findings f
        LEFT JOIN evidence e ON e.id = f.evidence_id
        WHERE NULLIF(BTRIM(COALESCE(f.location_description, '')), '') IS NOT NULL
          AND (f.evidence_id IS NULL OR e.id IS NULL)
    ) THEN
        RAISE EXCEPTION 'V39 cannot move finding location: a finding with a location has no linked evidence';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM findings f
        JOIN evidence e ON e.id = f.evidence_id
        WHERE NULLIF(BTRIM(COALESCE(f.location_description, '')), '') IS NOT NULL
          AND NULLIF(BTRIM(COALESCE(e.location_description, '')), '') IS NOT NULL
          AND BTRIM(e.location_description) IS DISTINCT FROM BTRIM(f.location_description)
    ) THEN
        RAISE EXCEPTION 'V39 cannot move finding location: finding and evidence locations conflict';
    END IF;

    IF EXISTS (
        SELECT f.evidence_id
        FROM findings f
        WHERE f.evidence_id IS NOT NULL
          AND NULLIF(BTRIM(COALESCE(f.location_description, '')), '') IS NOT NULL
        GROUP BY f.evidence_id
        HAVING COUNT(DISTINCT BTRIM(f.location_description)) > 1
    ) THEN
        RAISE EXCEPTION 'V39 cannot move finding location: multiple finding locations exist for one evidence item';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM inspections
        WHERE corrective_handoff_at IS NOT NULL
          AND (corrective_handoff_by_user_id IS NULL OR status NOT IN ('REPAIR_PENDING', 'COMPLETED'))
    ) THEN
        RAISE EXCEPTION 'V39 cannot preserve a legacy handoff: actor or terminal handoff status is missing';
    END IF;
END
$$;

-- Move the single non-conflicting legacy finding location onto its evidence item before removing
-- the duplicate finding field. Conflicts are rejected by the guards above.
UPDATE evidence e
SET location_description = source.location_description
FROM (
    SELECT evidence_id, MIN(BTRIM(location_description)) AS location_description
    FROM findings
    WHERE evidence_id IS NOT NULL
      AND NULLIF(BTRIM(COALESCE(location_description, '')), '') IS NOT NULL
    GROUP BY evidence_id
    HAVING COUNT(DISTINCT BTRIM(location_description)) = 1
) source
WHERE e.id = source.evidence_id
  AND NULLIF(BTRIM(COALESCE(e.location_description, '')), '') IS NULL;

-- Keep distinct notes, and avoid duplicating values that were previously copied between fields.
UPDATE findings f
SET technical_notes = CONCAT_WS(
    E'\n\n',
    NULLIF(BTRIM(f.technical_notes), ''),
    CASE
        WHEN NULLIF(BTRIM(COALESCE(f.description, '')), '') IS NOT NULL
             AND BTRIM(f.description) IS DISTINCT FROM BTRIM(COALESCE(f.technical_notes, ''))
        THEN 'Description: ' || BTRIM(f.description)
    END,
    CASE
        WHEN NULLIF(BTRIM(COALESCE(f.observed_condition, '')), '') IS NOT NULL
             AND BTRIM(f.observed_condition) IS DISTINCT FROM BTRIM(COALESCE(f.technical_notes, ''))
             AND BTRIM(f.observed_condition) IS DISTINCT FROM BTRIM(COALESCE(f.description, ''))
        THEN 'Observed condition: ' || BTRIM(f.observed_condition)
    END
)
WHERE f.description IS NOT NULL OR f.observed_condition IS NOT NULL;

-- New per-finding state is recoverable for completed legacy handoffs: V30/V31 required a recorded
-- reviewer outcome for each repair-required finding before the aggregate handoff could finish.
ALTER TABLE findings ADD COLUMN forwarded_to_maintenance_at TIMESTAMPTZ;
UPDATE findings f
SET forwarded_to_maintenance_at = i.corrective_handoff_at
FROM inspections i
WHERE i.id = f.inspection_id
  AND i.corrective_handoff_at IS NOT NULL
  AND i.status IN ('REPAIR_PENDING', 'COMPLETED')
  AND f.status = 'CONFIRMED'
  AND f.corrective_action_reviewed_by_user_id IS NOT NULL
  AND f.corrective_action_required
  AND f.repair_required;

-- Preserve the historical aggregate decision actor/time/reason in the existing audit stream. The
-- finding IDs are taken only from the explicit legacy repair disposition; no missing selection is
-- inferred. New handoffs also write an audit event from ReportService.
INSERT INTO audit_events (
    organization_id, actor_user_id, action, aggregate_type, aggregate_id,
    before_status, after_status, reason, safe_metadata, created_at)
SELECT i.organization_id,
       i.corrective_handoff_by_user_id,
       'MF3_LEGACY_MAINTENANCE_HANDOFF',
       'INSPECTION',
       i.id,
       'REPORT_PUBLISHED',
       i.status,
       i.no_repair_rationale,
       jsonb_build_object(
           'forwardedFindingIds', COALESCE(
               jsonb_agg(f.id) FILTER (WHERE f.forwarded_to_maintenance_at IS NOT NULL), '[]'::jsonb),
           'source', 'V38 inspection handoff fields'),
       i.corrective_handoff_at
FROM inspections i
LEFT JOIN findings f ON f.inspection_id = i.id
WHERE i.corrective_handoff_at IS NOT NULL
GROUP BY i.id, i.organization_id, i.corrective_handoff_by_user_id, i.status,
         i.no_repair_rationale, i.corrective_handoff_at;

DROP TABLE evidence_quality_decisions;

ALTER TABLE evidence
    DROP COLUMN uploaded_by_user_id,
    DROP COLUMN checksum_sha256,
    DROP COLUMN source,
    DROP COLUMN external_reference,
    DROP COLUMN capture_metadata,
    DROP COLUMN immutable_original,
    DROP COLUMN retired_by_user_id,
    DROP COLUMN retirement_reason,
    DROP COLUMN analysis_completed_at,
    DROP COLUMN analysis_detection_count,
    DROP COLUMN kind;

ALTER TABLE findings
    DROP COLUMN created_by_user_id,
    DROP COLUMN location_description,
    DROP COLUMN confirmed_by_user_id,
    DROP COLUMN component,
    DROP COLUMN description,
    DROP COLUMN observed_condition,
    DROP COLUMN priority,
    DROP COLUMN measurement,
    DROP COLUMN corrective_action_required,
    DROP COLUMN corrective_action_priority,
    DROP COLUMN corrective_action_due_at,
    DROP COLUMN corrective_action_reviewed_by_user_id,
    DROP COLUMN corrective_action_reviewed_at,
    DROP COLUMN corrective_action_rationale,
    DROP COLUMN repair_required,
    DROP COLUMN deleted_by_user_id,
    DROP COLUMN deletion_reason,
    DROP COLUMN row_version;

ALTER TABLE inspection_report_versions
    DROP COLUMN author_user_id,
    DROP COLUMN author_verified_by_user_id,
    DROP COLUMN author_verified_at,
    DROP COLUMN llm_provider,
    DROP COLUMN llm_model,
    DROP COLUMN prompt_version,
    DROP COLUMN generated_at,
    DROP COLUMN evidence_snapshot_hash,
    DROP COLUMN findings_snapshot_hash,
    DROP COLUMN withdrawn_by_user_id,
    DROP COLUMN return_reason_type;

ALTER TABLE inspection_report_versions
    RENAME COLUMN review_reason TO return_reason;

-- Existing rows are intentionally left NULL: the version table never recorded a reliable submit
-- event, so created_at/author_verified_at are not fabricated as submitted_at.
ALTER TABLE inspection_report_versions ADD COLUMN submitted_at TIMESTAMPTZ;

ALTER TABLE inspections
    DROP COLUMN findings_confirmed_by_user_id,
    DROP COLUMN corrective_handoff_by_user_id,
    DROP COLUMN corrective_handoff_at,
    DROP COLUMN no_repair_rationale;

ALTER TABLE field_sessions DROP COLUMN checklist_version;

ALTER TABLE inspection_readiness_decisions
    DROP COLUMN permit_snapshot_ids,
    DROP COLUMN credential_snapshot_ids,
    DROP COLUMN drone_document_snapshot_ids,
    DROP COLUMN created_at;
