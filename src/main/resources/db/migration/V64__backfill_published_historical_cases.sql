-- Backfill existing published inspection findings into the RAG knowledge store and propagate
-- any verification outcomes recorded on published re-inspection reports.
INSERT INTO historical_finding_cases (
    source_finding_id,
    source_inspection_id,
    organization_id,
    asset_id,
    asset_category_code,
    asset_label,
    defect_label,
    severity,
    technical_notes,
    report_outcome,
    is_anonymized_benchmark,
    published_at
)
SELECT
    f.id,
    i.id,
    i.owner_customer_id,
    i.asset_id,
    COALESCE(NULLIF(BTRIM(c.code), ''), 'ALL'),
    COALESCE(
        LEFT(NULLIF(BTRIM(a.name), ''), 255),
        COALESCE(NULLIF(BTRIM(c.code), ''), 'ALL') || ' asset'
    ),
    f.defect_label,
    f.severity,
    f.technical_notes,
    COALESCE(
        (
            SELECT v.resolution_status
            FROM reinspection_defect_verifications v
            JOIN inspections ri ON ri.id = v.reinspection_id
            WHERE v.source_finding_id = f.id
              AND EXISTS (
                  SELECT 1
                  FROM inspection_reports rr
                  WHERE rr.inspection_id = ri.id
                    AND rr.status = 'PUBLISHED'
              )
            ORDER BY v.updated_at DESC, v.created_at DESC
            LIMIT 1
        ),
        'PUBLISHED'
    ),
    FALSE,
    COALESCE(
        (
            SELECT MAX(ir.published_at)
            FROM inspection_reports ir
            WHERE ir.inspection_id = i.id
              AND ir.status = 'PUBLISHED'
        ),
        CURRENT_TIMESTAMP
    )
FROM findings f
JOIN inspections i ON i.id = f.inspection_id
JOIN assets a ON a.id = i.asset_id
LEFT JOIN asset_categories c ON c.id = a.category_id
WHERE f.status = 'CONFIRMED'
  AND f.is_deleted = FALSE
  AND EXISTS (
      SELECT 1
      FROM inspection_reports r
      WHERE r.inspection_id = i.id
        AND r.status = 'PUBLISHED'
  )
ON CONFLICT (source_finding_id) DO UPDATE
SET defect_label = EXCLUDED.defect_label,
    severity = EXCLUDED.severity,
    technical_notes = EXCLUDED.technical_notes,
    report_outcome = EXCLUDED.report_outcome;
