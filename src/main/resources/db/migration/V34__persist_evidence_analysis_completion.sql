ALTER TABLE evidence
    ADD COLUMN analysis_completed_at TIMESTAMPTZ,
    ADD COLUMN analysis_detection_count INTEGER,
    ADD CONSTRAINT ck_evidence_analysis_completion CHECK (
        (analysis_completed_at IS NULL AND analysis_detection_count IS NULL)
        OR (analysis_completed_at IS NOT NULL AND analysis_detection_count >= 0)
    );
