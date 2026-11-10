-- 1. Persist an automatically generated summary on each inspection report version
-- so it is created once at the Report step and forwarded directly to Manager and Customer views.

ALTER TABLE inspection_reports
    ADD COLUMN IF NOT EXISTS summary TEXT;

UPDATE inspection_reports r
SET summary = CASE
    WHEN COALESCE(stats.finding_count, 0) = 0 THEN
        'Drone aerial visual inspection completed. No structural defects were observed within the inspected scope. Overall asset condition is assessed as Good.'
    ELSE
        'Drone aerial visual inspection completed with ' || stats.finding_count || ' confirmed defect(s). Overall condition risk is assessed as ' ||
        CASE
            WHEN stats.has_critical THEN 'CRITICAL'
            WHEN stats.has_high THEN 'HIGH'
            WHEN stats.has_medium THEN 'MEDIUM'
            ELSE 'LOW'
        END || '.'
END
FROM (
    SELECT
        i.id AS inspection_id,
        COUNT(f.id) FILTER (WHERE f.status = 'CONFIRMED' AND f.is_deleted = FALSE) AS finding_count,
        BOOL_OR(f.severity = 'CRITICAL') FILTER (WHERE f.status = 'CONFIRMED' AND f.is_deleted = FALSE) AS has_critical,
        BOOL_OR(f.severity = 'HIGH') FILTER (WHERE f.status = 'CONFIRMED' AND f.is_deleted = FALSE) AS has_high,
        BOOL_OR(f.severity = 'MEDIUM') FILTER (WHERE f.status = 'CONFIRMED' AND f.is_deleted = FALSE) AS has_medium
    FROM inspections i
    LEFT JOIN findings f ON f.inspection_id = i.id
    GROUP BY i.id
) stats
WHERE r.inspection_id = stats.inspection_id
  AND (r.summary IS NULL OR BTRIM(r.summary) = '');

-- 2. Platform AI confidence threshold & model telemetry settings for Admin
CREATE TABLE IF NOT EXISTS platform_ai_settings (
    id INT PRIMARY KEY DEFAULT 1,
    auto_confirm_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auto_confirm_threshold NUMERIC(5, 2) NOT NULL DEFAULT 85.00,
    auto_confirmed_count BIGINT NOT NULL DEFAULT 0,
    rejected_pending_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    CONSTRAINT ck_platform_ai_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_platform_ai_settings_threshold CHECK (auto_confirm_threshold >= 0 AND auto_confirm_threshold <= 100)
);

INSERT INTO platform_ai_settings (id, auto_confirm_enabled, auto_confirm_threshold, auto_confirmed_count, rejected_pending_count)
VALUES (1, TRUE, 85.00, 0, 0)
ON CONFLICT (id) DO NOTHING;
