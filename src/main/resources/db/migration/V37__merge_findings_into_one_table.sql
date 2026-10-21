-- MF3 findings: one table for every finding, whoever produced it.
--
-- The two tables this replaces held one idea in two shapes. A detector suggestion and an
-- Inspector's own observation were stored apart, so the workflow had to decide which one a row
-- was by looking at a source column, and the review state was duplicated across two vocabularies
-- that disagreed with each other. The source column in particular recorded a distinction the
-- workflow never asked for: it only ever needed to know whether the Inspector had confirmed the
-- finding.
--
-- The merged table keeps what each row actually knows. A finding carries the model that produced
-- it, its confidence and its box when there was one, and a status that says the only thing the
-- workflow asks: has the Inspector confirmed it. Removing a finding is a tombstone rather than a
-- status, so a finding that was confirmed and later removed still records that it was confirmed.
--
-- The table is renamed rather than rebuilt, so the report and work-order references to a finding
-- keep pointing at the same row. The rows are merged, not copied: a suggestion the Inspector
-- already resolved becomes the finding it produced, keeping that finding's code so nothing
-- already published shifts.

ALTER TABLE verified_findings RENAME TO findings;

-- The review state is the status now, so the columns that duplicated it go, along with the
-- vocabulary they carried. The constraint that tied the old status to a resolution timestamp
-- goes with them.
ALTER TABLE findings
    DROP CONSTRAINT ck_verified_finding_source,
    DROP CONSTRAINT ck_verified_finding_source_reference,
    DROP CONSTRAINT ck_verified_finding_status,
    DROP CONSTRAINT ck_verified_finding_resolution;

ALTER TABLE findings
    DROP COLUMN source,
    DROP COLUMN decision,
    DROP COLUMN decided_by_user_id,
    DROP COLUMN decided_at,
    DROP COLUMN rationale,
    DROP COLUMN resolved_at;

-- The provenance of a finding that came from a suggestion travels with it, so the suggestion that
-- was judged stays traceable after the separate table is gone.
ALTER TABLE findings
    ADD COLUMN model_name VARCHAR(160),
    ADD COLUMN model_version VARCHAR(80),
    ADD COLUMN model_provider VARCHAR(96),
    ADD COLUMN confidence NUMERIC(6, 5);

-- Who confirmed a finding is part of the record, so the report can name them.
ALTER TABLE findings
    ADD COLUMN confirmed_by_user_id UUID REFERENCES users (id) ON DELETE RESTRICT;

UPDATE findings f
SET model_name = c.model_name,
    model_version = c.model_version,
    model_provider = c.model_provider,
    confidence = c.confidence
FROM ai_finding_candidates c
WHERE c.id = f.ai_candidate_id;

-- The findings that are already official keep their identity: the confirmation time and the
-- corrective outcome they were published with.
UPDATE findings
SET status = CASE WHEN inspector_confirmed_at IS NOT NULL THEN 'CONFIRMED' ELSE 'PENDING' END;

UPDATE findings f
SET confirmed_by_user_id = f.created_by_user_id
WHERE f.inspector_confirmed_at IS NOT NULL
  AND f.confirmed_by_user_id IS NULL;

ALTER TABLE findings
    ADD CONSTRAINT ck_findings_status CHECK (status IN ('PENDING', 'CONFIRMED')),
    ADD CONSTRAINT ck_findings_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    ADD CONSTRAINT ck_findings_confirmation CHECK (
        (status = 'CONFIRMED' AND inspector_confirmed_at IS NOT NULL)
            OR (status = 'PENDING' AND inspector_confirmed_at IS NULL)
        );

-- A suggestion the Inspector never resolved stays a finding of its own, waiting to be looked at.
-- A suggestion that already produced a finding is that finding, so it is not copied again.
INSERT INTO findings (
    id, inspection_id, evidence_id, created_by_user_id, status, finding_code, defect_label,
    severity, location_description, bounding_box, technical_notes, recommended_action,
    model_name, model_version, model_provider, confidence, component, description,
    observed_condition, priority, measurement, corrective_action_required, repair_required,
    created_at, updated_at, row_version)
SELECT c.id,
       c.inspection_id,
       c.evidence_id,
       e.uploaded_by_user_id,
       'PENDING',
       'F-' || (base.next_code + ROW_NUMBER() OVER (PARTITION BY c.inspection_id ORDER BY c.created_at, c.id)),
       c.predicted_label,
       'MEDIUM',
       NULL,
       c.bounding_box,
       c.predicted_label,
       NULL,
       c.model_name,
       c.model_version,
       c.model_provider,
       c.confidence,
       NULL,
       NULL,
       NULL,
       NULL,
       NULL,
       TRUE,
       FALSE,
       c.created_at,
       c.created_at,
       0
FROM ai_finding_candidates c
         JOIN evidence e ON e.id = c.evidence_id
         CROSS JOIN LATERAL (
    SELECT COALESCE(MAX((regexp_match(f2.finding_code, '^F-([0-9]+)$'))[1]::BIGINT), 0) AS next_code
    FROM findings f2
    WHERE f2.inspection_id = c.inspection_id
    ) base
WHERE c.inspection_id IS NOT NULL
  AND c.id NOT IN (SELECT f.ai_candidate_id FROM findings f WHERE f.ai_candidate_id IS NOT NULL)
  AND c.status <> 'REJECTED';

-- The link to the suggestion it came from has served its purpose: the suggestion is now the
-- finding.
ALTER TABLE findings DROP COLUMN ai_candidate_id;

-- Rejected AI suggestions are not findings and are not retained as finding-level audit records.
DELETE FROM ai_finding_candidates WHERE status = 'REJECTED';

DROP TABLE ai_finding_candidates;

-- A table rename does not rename its index, so the index is dropped and recreated under the
-- name the merged table now carries.
ALTER TABLE findings RENAME CONSTRAINT uq_verified_findings_code TO uq_findings_code;

DROP INDEX ix_verified_findings_inspection_status;

CREATE INDEX ix_findings_inspection_status ON findings (inspection_id, status, severity);
CREATE INDEX ix_findings_evidence ON findings (evidence_id);
